import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.requests.data.artists.GetArtistRequest;
import org.apache.hc.core5.http.ParseException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

class AuthManager {

    private static final Logger logger = LoggerFactory.getLogger("spotify-cli-java.AuthManager");
    // Fields that must be set at object instantiation
    private ITokenCache tokenCache;
    private final SpotifyApi spotifyApi;
    private final IAuthorizationFlow authorizationFlow;
    private final boolean disableTokenCaching;
    private final boolean tokenCachingEnabled;
    private boolean disableTokenRefresh;
    private boolean tokenRefreshEnabled;

    private AuthManager(Builder builder) {
        authorizationFlow = builder.authorizationFlow;
        spotifyApi = builder.spotifyApi;

        if (builder.tokenCache != null) {
            tokenCache = builder.tokenCache;
            disableTokenRefresh = builder.disableTokenRefresh;
            disableTokenCaching = builder.disableTokenCaching;
            tokenCachingEnabled = !this.disableTokenCaching;
            tokenRefreshEnabled = !this.disableTokenRefresh;
        } else {
            disableTokenCaching = true;
            tokenCachingEnabled = false;
        }
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

        GenericCredentials genericCredentials;

        // Try to use tokens from the cache
        if (tokenCachingEnabled && tokenCache.isValid()) {
            logger.info("Attempting to load tokens from the cache");
            genericCredentials = tokenCache.loadTokens();
            if (genericCredentials != null) {
                setTokensOnSpotifyInstance(genericCredentials);

                // Test to see if the cached access token is still valid
                String msg = testSpotifyConnection();
                if (msg.equals("")) {
                    return AuthStatus.SUCCESS;
                } else {
                    logger.info(msg);
                    logger.info("Cached token was invalid");
                }
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
                && tokenCache.isValid()) {
            logger.info("Attempting to refresh the access token using the cached refresh token");
            genericCredentials = authorizationFlow.refresh();
            if (genericCredentials != null) {
                setTokensOnSpotifyInstance(genericCredentials);

                String msg = testSpotifyConnection();
                if (msg.equals("")) {
                    if (tokenCachingEnabled)
                        tokenCache.cacheTokens(genericCredentials);
                    logger.info("Successfully refreshed the access token");
                    return AuthStatus.SUCCESS;
                }
            }
        }


        // If valid tokens could not be retrieved any other way, require a full refresh
        // This may mean the end user will have to sign in
        logger.info("A full authorization is required, end user may be required to sign in");
        genericCredentials = authorizationFlow.authorize();

        if (tokenCachingEnabled)
            tokenCache.cacheTokens(genericCredentials);

        setTokensOnSpotifyInstance(genericCredentials);
        String msg = testSpotifyConnection();
        if (msg.equals("")) {
            logger.info("Successfully retrieved a new access token from Spotify");
            return AuthStatus.SUCCESS;
        }

        logger.info(msg);
        return AuthStatus.FAIL;
    }

    private void setTokensOnSpotifyInstance(@NotNull GenericCredentials genericCredentials) {
        spotifyApi.setAccessToken(genericCredentials.getAccessToken());
        // Not all auth flows return a refresh token, namely, Implicit Grand flow and Client Credentials flow
        String refreshToken = genericCredentials.getRefreshToken();
        if (this.authorizationFlow.isRefreshable() && refreshToken != null && !refreshToken.equals("")) {
            spotifyApi.setRefreshToken(refreshToken);
        }
    }


    /**
     * @return Upon failure, a string containing an error message, or upon success, an empty string.
     */
    private String testSpotifyConnection() {
        // Test token actually works. ID = Weezer , could probably switch to something else
        GetArtistRequest getArtistRequest = spotifyApi.getArtist("3jOstUTkEu2JkjvRdBA5Gu")
                .build();
        try {
            getArtistRequest.execute();
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            logger.info("Spotify connection test with current tokens FAILED");
            return e.getMessage();
        }
        logger.info("Spotify connection test with current tokens SUCCEEDED");
        return "";
    }

    public enum AuthStatus {
        SUCCESS,
        FAIL
    }

    public static class Builder {
        private final SpotifyApi spotifyApi;
        private final IAuthorizationFlow authorizationFlow;
        private ITokenCache tokenCache;

        private boolean disableTokenRefresh = true;
        private boolean disableTokenCaching = true;

        // TODO: Change type of authorizationFlow to IAuthFlow?
        public Builder(@NotNull IAuthorizationFlow authorizationFlow, @NotNull SpotifyApi spotifyApi) {
            this.authorizationFlow = authorizationFlow;
            this.spotifyApi = spotifyApi;
        }

        public Builder withTokenCache(TokenCache TokenCache) {
            this.tokenCache = TokenCache;
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