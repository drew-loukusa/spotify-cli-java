import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.requests.data.artists.GetArtistRequest;

import kotlin.Pair;
import org.apache.hc.core5.http.ParseException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

class AuthManager {

    private static final Logger logger = (Logger) LoggerFactory.getLogger("spotify-cli-java.AuthManager");

    public enum Authentication {
        SUCCESS,
        FAIL
    }

    public enum CacheState{
        CACHE_DNE,
        ACCESS_TOKEN_EXPIRED,
        ACCESS_TOKEN_VALID,
        ACCESS_TOKEN_INVALID,
    }

    // Provide default, but don't make final so path can be changed by user
    private String TOKEN_CACHE_PATH = "token_cache.txt";

    // Fields that must be set at object instantiation
    private SpotifyApi spotifyApi;
    private AbstractAuthorizationFlow authorizationFlow;
    private boolean refreshEnabled;
    private boolean tokenCachingEnabled;

    // Fields that are set and managed after object instantiation (Thus are not in builder)
    private String accessToken = "";
    private String refreshToken = "";
    private String accessDuration = "NULL";
    private String accessCreationTimeStamp = "NULL";

    private AuthManager(Builder builder){
        if (!builder.tokenCacheFilePath.equals("")){
            TOKEN_CACHE_PATH = builder.tokenCacheFilePath;
        }
        this.authorizationFlow = builder.authorizationFlow;
        this.spotifyApi = builder.spotifyApi;
        this.refreshEnabled = builder.refreshEnabled;
        this.tokenCachingEnabled = builder.tokenCachingEnabled;
    }

    public static class Builder {
        private String tokenCacheFilePath = "";
        private SpotifyApi spotifyApi;
        private AbstractAuthorizationFlow authorizationFlow;
        private boolean refreshEnabled = true;
        private boolean tokenCachingEnabled = true;
        public Builder(@NotNull AbstractAuthorizationFlow authorizationFlow, @NotNull SpotifyApi spotifyApi){
            this.authorizationFlow = authorizationFlow;
            this.spotifyApi = spotifyApi;
        }

        public Builder tokenCacheFilePath(String tokenCacheFilePath){
            this.tokenCacheFilePath = tokenCacheFilePath;
            return this;
        }

        /**
         * Disables token refreshing: I.E. If a cached access token is invalid or expired, the app will not try
         * to refresh it. Instead, a new token is acquired through the configured authentication flow
         */
        public Builder disableRefresh(){
            this.refreshEnabled = false;
            return this;
        }

        /**
         * Disables token caching. NOTE: Also disables token refresh, since token refresh relies on the token cache
         */
        public Builder disableTokenCaching(){
            this.tokenCachingEnabled = false;
            return this;
        }

        public AuthManager build(){
            return new AuthManager(this);
        }
    }

    public Authentication authenticateSpotifyInstance(){

        if (tokenCachingEnabled) {
            logger.info("Token caching enabled");
        } else {
            logger.info("Token caching disabled");
        }
        if (refreshEnabled) {
            logger.info("Token refresh enabled");
        } else{
            logger.info("Token refresh disabled");
        }

        // Try to load tokens from the cache
        if (tokenCachingEnabled) {
            var cacheState = loadTokensFromCache();
            if (cacheState == CacheState.ACCESS_TOKEN_VALID) {
                setTokensOnSpotifyInstance();

                //If connection is good, tokens are good
                var pair = testSpotifyConnection();
                if (pair.getFirst()) {
                    return Authentication.SUCCESS;
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

        // Try to refresh the access token, provided that selected auth flow supports refresh and the token cache exists
        if (tokenCachingEnabled && refreshEnabled) {
            var cacheState = loadTokensFromCache();
            if (authorizationFlow.isRefreshable() && cacheState != CacheState.CACHE_DNE) {
                if (authRefresh()) {
                    setTokensOnSpotifyInstance();

                    var pair = testSpotifyConnection();
                    if (pair.getFirst()) {
                        logger.info("Successfully refreshed the access token");
                        return Authentication.SUCCESS;
                    }
                    if (tokenCachingEnabled)
                        cacheTokensToFile();
                }
            }
        }

        // If valid tokens could not be retrieved any other way, require a full refresh
        // Typically this means the user will have to sign in
        fullAuthRefresh();
        if (tokenCachingEnabled)
            cacheTokensToFile();
        setTokensOnSpotifyInstance();
        var pair = testSpotifyConnection();
        if (pair.getFirst()){
            logger.info("Successfully retrieved a new access token from Spotify");
            return Authentication.SUCCESS;
        }

        logger.info(pair.getSecond());
        return Authentication.FAIL;
    }

    private void cacheTokensToFile(){
        try (var fileWriter = new FileWriter(TOKEN_CACHE_PATH)) {
            fileWriter.write("ACCESS_TOKEN\t" + accessToken + "\n");
            fileWriter.write("REFRESH_TOKEN\t" + refreshToken + "\n");
            fileWriter.write("ACCESS_DURATION_SECONDS\t" + accessDuration + "\n");
            fileWriter.write("ACCESS_CREATION_TIMESTAMP\t" + accessCreationTimeStamp + "\n");
            fileWriter.write("ACCESS_CREATION_FORMAT\tHH:MM:SS\n");
            logger.info(String.format("Cached tokens to file with name \"%s\"", TOKEN_CACHE_PATH));
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

    private CacheState loadTokensFromCache() {
        // If cache file has not been created yet
        if (Files.notExists(Paths.get(TOKEN_CACHE_PATH))) {
            return CacheState.CACHE_DNE;
        }

        try {
            List<String> lines = Files.readAllLines(Paths.get(TOKEN_CACHE_PATH));
            accessToken = lines.get(0).split("\t")[1];
            refreshToken = lines.get(1).split("\t")[1];
            accessDuration = lines.get(2).split("\t")[1];
            accessCreationTimeStamp = lines.get(3).split("\t")[1];
            logger.info(String.format("Loaded tokens from file with name \"%s\"", TOKEN_CACHE_PATH));
        } catch (IOException e) {
            e.printStackTrace();
        }
        // TODO: Implement checking if token is expired based on timestamp
        // If the access token is expired
        // if ( ... convert things to ints, do math ) { return CacheState.ACCESS_TOKEN_EXPIRED; }

        return CacheState.ACCESS_TOKEN_VALID;
    }

    /**
     * Using authorizationFlow, attempts to refresh the existing access token using the existing refresh token.
     * Sets accessToken, refreshToken, accessDuration, and accessCreationTimeStamp if successful.
     * NOTE: ONLY WORKS if the authorization flow type in use supports token refreshing, AND token caching is enabled.
     *
     * NOTE: Call authorizationFlow.isRefreshable() to check if an auth flow supports refreshing before calling this
     * method.
     *
     * @return Success value as a boolean indicating if the auth flow was able to refresh the access token.
     */
    private boolean authRefresh(){
        GenericCredentials credentials = authorizationFlow.refresh();
        if (credentials == null)
            return false;
        accessToken = credentials.getAccessToken();
        // TODO: What happens when an auth flow doesn't supply a refresh token?
        refreshToken = credentials.getRefreshToken();
        accessDuration = credentials.getExpiresIn().toString();
        // TODO: Add getting current time in HH:MM:SS format
        //this.accessCreationTimeStamp =
        return true;
    }

    /**
     * Using 'authorizationFlow', attempts to set, accessToken, refreshToken, accessDuration,
     * and accessCreationTimeStamp. Depending on what authorization flow type is being used,
     * the end user may be required to sign in.
     */
    private void fullAuthRefresh(){
        GenericCredentials credentials = authorizationFlow.authorize();

        accessToken = credentials.getAccessToken();
        // TODO: What happens when an auth flow doesn't supply a refresh token?
        refreshToken = credentials.getRefreshToken();
        accessDuration = credentials.getExpiresIn().toString();
        // TODO: Add getting current time in HH:MM:SS format
        //this.accessCreationTimeStamp =
    }

    private void setTokensOnSpotifyInstance(){
        spotifyApi.setAccessToken(this.accessToken);
        // Not all auth flows return a refresh token, namely, Implicit Grand flow and Client Credentials flow
        if (this.authorizationFlow.isRefreshable() && this.refreshToken != null && !this.refreshToken.equals("")) {
            spotifyApi.setRefreshToken(this.refreshToken);
        }
    }

    private Pair<Boolean, String> testSpotifyConnection(){
        // Test token actually works. ID = Weezer , could probably switch to something else
        GetArtistRequest getArtistRequest = spotifyApi.getArtist("3jOstUTkEu2JkjvRdBA5Gu")
                .build();
        try {
            getArtistRequest.execute();
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            return new Pair<>(false, e.getMessage());
        }
        return new Pair<>(true, "");
    }
}
