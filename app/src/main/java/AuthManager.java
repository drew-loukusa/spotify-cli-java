import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.requests.data.artists.GetArtistRequest;

import kotlin.Pair;
import org.apache.hc.core5.http.ParseException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

class AuthManager {

    private static final Logger logger = LoggerFactory.getLogger("spotify-cli-java.AuthManager");
    // Fields that must be set at object instantiation
    private final SpotifyApi spotifyApi;
    private final AbstractAuthorizationFlow authorizationFlow;
    private final boolean disableTokenRefresh;
    private final boolean disableTokenCaching;
    private final boolean tokenRefreshEnabled;
    private final boolean tokenCachingEnabled;
    // Provide default, but don't make final so path can be changed by user
    private String TOKEN_CACHE_PATH = "token_cache.txt";

    private AuthManager(Builder builder) {
        if (!builder.tokenCacheFilePath.equals("")) {
            TOKEN_CACHE_PATH = builder.tokenCacheFilePath;
        }
        this.authorizationFlow = builder.authorizationFlow;
        this.spotifyApi = builder.spotifyApi;
        this.disableTokenRefresh = builder.disableTokenRefresh;
        this.disableTokenCaching = builder.disableTokenCaching;

        this.tokenCachingEnabled = !this.disableTokenCaching;
        this.tokenRefreshEnabled = !this.disableTokenRefresh;
    }

    public AuthStatus authenticateSpotifyInstance() {
        if (disableTokenCaching) {
            logger.info("Token caching disabled");
        } else {
            logger.info("Token caching enabled");
        }
        if (disableTokenRefresh) {
            logger.info("Token refresh disabled");
        } else {
            logger.info("Token refresh enabled");
        }

        // Check the state of the cache
        var loadPair = loadTokensFromCache();
        AuthManager.CacheState cacheState = loadPair.getFirst();
        GenericCredentials genericCredentials = loadPair.getSecond();

        // Try to use tokens from the cache
        if (tokenCachingEnabled) {
            if (cacheState == CacheState.ACCESS_TOKEN_VALID) {
                setTokensOnSpotifyInstance(genericCredentials);

                // Test to see if the cached access token is still valid
                var pair = testSpotifyConnection();
                if (pair.getFirst()) {
                    return AuthStatus.SUCCESS;
                }

                logger.info(pair.getSecond());
                cacheState = CacheState.ACCESS_TOKEN_INVALID;
            }
            if (cacheState == CacheState.CACHE_DNE) {
                logger.info("Token cache does not exist");
            } else if (cacheState == CacheState.ACCESS_TOKEN_EXPIRED) {
                logger.info("Cached token was expired");
            } else if (cacheState == CacheState.ACCESS_TOKEN_INVALID) {
                logger.info("Cached token was invalid");
            }
        }

        /*
         * Try to refresh the access token, provided the authorization flow type in use supports token refreshing,
         * token caching is enabled, and the token cache exists.
         *
         * Sets accessToken, refreshToken, accessDuration, and accessCreationTimeStamp if successful.
         *
         * NOTE: Always call authorizationFlow.isRefreshable() to check if an auth flow supports refreshing before
         * trying to invoke refresh on it
         */
        if (tokenCachingEnabled
                && tokenRefreshEnabled
                && authorizationFlow.isRefreshable()
                && cacheState != CacheState.CACHE_DNE) {

            genericCredentials = authorizationFlow.refresh();
            if (genericCredentials != null) {
                setTokensOnSpotifyInstance(genericCredentials);

                var pair = testSpotifyConnection();
                if (pair.getFirst()) {
                    if (tokenCachingEnabled)
                        cacheTokensToFile(genericCredentials);
                    logger.info("Successfully refreshed the access token");
                    return AuthStatus.SUCCESS;
                }
            }
        }

        // If valid tokens could not be retrieved any other way, require a full refresh
        // This may mean the end user will have to sign in
        genericCredentials = authorizationFlow.authorize();

        if (tokenCachingEnabled)
            cacheTokensToFile(genericCredentials);

        setTokensOnSpotifyInstance(genericCredentials);
        var pair = testSpotifyConnection();
        if (pair.getFirst()) {
            logger.info("Successfully retrieved a new access token from Spotify");
            return AuthStatus.SUCCESS;
        }

        logger.info(pair.getSecond());
        return AuthStatus.FAIL;
    }

    private void cacheTokensToFile(@NotNull GenericCredentials genericCredentials) {
        String accessToken = genericCredentials.getAccessToken();
        String refreshToken = genericCredentials.getRefreshToken();
        String accessDuration = genericCredentials.getExpiresIn().toString();
        String accessCreationTimeStamp = genericCredentials.getAccessCreationTimeStamp();
        try (var fileWriter = new FileWriter(TOKEN_CACHE_PATH)) {
            fileWriter.write("ACCESS_TOKEN\t" + accessToken
                    + "\nREFRESH_TOKEN\t" + refreshToken
                    + "\nACCESS_DURATION_SECONDS\t" + accessDuration
                    + "\nACCESS_CREATION_TIMESTAMP\t" + accessCreationTimeStamp
                    + "\nACCESS_CREATION_FORMAT\tYY:MM:DD:HH:MM:SS\n"
            );
            logger.info(String.format("Cached tokens to file with name \"%s\"", TOKEN_CACHE_PATH));
            logger.debug("Wrote access token: " + accessToken + "\nWrote refresh token: " + refreshToken
                    + "\nWrote access token duration: " + accessDuration
                    + "\nWrote access token timestamp: " + accessCreationTimeStamp
            );
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

    private Pair<CacheState, GenericCredentials> loadTokensFromCache() {
        // If cache file has not been created yet
        if (Files.notExists(Paths.get(TOKEN_CACHE_PATH))) {
            return new Pair<>(CacheState.CACHE_DNE, null);
        }

        GenericCredentials genericCredentials = null;
        try {
            List<String> lines = Files.readAllLines(Paths.get(TOKEN_CACHE_PATH));
            String accessToken = lines.get(0).split("\t")[1];
            String refreshToken = lines.get(1).split("\t")[1];
            String accessDuration = lines.get(2).split("\t")[1];
            String accessCreationTimeStamp = lines.get(3).split("\t")[1];
            logger.info(String.format("Loaded tokens from file with name \"%s\"", TOKEN_CACHE_PATH));
            logger.debug("Loaded access token: " + accessToken);
            logger.debug("Loaded refresh token: " + refreshToken);
            logger.debug("Loaded access token duration: " + accessDuration);
            logger.debug("Loaded access token timestamp: " + accessCreationTimeStamp);

            genericCredentials = new GenericCredentials.Builder()
                    .withAccessToken(accessToken)
                    .withRefreshToken(refreshToken)
                    .withExpiresIn(Integer.valueOf(accessDuration))
                    .withAccessCreationTimeStamp(accessCreationTimeStamp)
                    .build();

        } catch (IOException e) {
            e.printStackTrace();

        }
        // TODO: Implement checking if token is expired based on timestamp
        // If the access token is expired
        // if ( ... convert things to ints, do math ) { return CacheState.ACCESS_TOKEN_EXPIRED; }

        return new Pair<>(CacheState.ACCESS_TOKEN_VALID, genericCredentials);
    }

    private void setTokensOnSpotifyInstance(@NotNull GenericCredentials genericCredentials) {
        spotifyApi.setAccessToken(genericCredentials.getAccessToken());
        // Not all auth flows return a refresh token, namely, Implicit Grand flow and Client Credentials flow
        String refreshToken = genericCredentials.getRefreshToken();
        if (this.authorizationFlow.isRefreshable() && refreshToken != null && !refreshToken.equals("")) {
            spotifyApi.setRefreshToken(refreshToken);
        }
    }

    private Pair<Boolean, String> testSpotifyConnection() {
        // Test token actually works. ID = Weezer , could probably switch to something else
        GetArtistRequest getArtistRequest = spotifyApi.getArtist("3jOstUTkEu2JkjvRdBA5Gu")
                .build();
        try {
            getArtistRequest.execute();
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            logger.info("Connection test with current tokens failed");
            return new Pair<>(false, e.getMessage());
        }
        return new Pair<>(true, "");
    }

    public enum AuthStatus {
        SUCCESS,
        FAIL
    }

    public enum CacheState {
        CACHE_DNE,
        ACCESS_TOKEN_EXPIRED,
        ACCESS_TOKEN_VALID,
        ACCESS_TOKEN_INVALID,
    }

    public static class Builder {
        private final SpotifyApi spotifyApi;
        private final AbstractAuthorizationFlow authorizationFlow;
        private String tokenCacheFilePath = "";
        private boolean disableTokenRefresh = true;
        private boolean disableTokenCaching = true;

        public Builder(@NotNull AbstractAuthorizationFlow authorizationFlow, @NotNull SpotifyApi spotifyApi) {
            this.authorizationFlow = authorizationFlow;
            this.spotifyApi = spotifyApi;
        }

        public Builder withTokenCacheFilePath(String tokenCacheFilePath) {
            this.tokenCacheFilePath = tokenCacheFilePath;
            return this;
        }

        /**
         * Disables token refreshing: I.E. If a cached access token is invalid or expired, the app will not try
         * to refresh it. Instead, a new token is acquired through the configured authentication flow
         */
        public Builder withDisableTokenRefresh(boolean bool) {
            this.disableTokenRefresh = bool;
            return this;
        }

        /**
         * Disables token caching. NOTE: Also disables token refresh, since token refresh relies on the token cache
         */
        public Builder withDisableTokenCaching(boolean bool) {
            this.disableTokenCaching = bool;
            return this;
        }

        public AuthManager build() {
            return new AuthManager(this);
        }
    }
}
