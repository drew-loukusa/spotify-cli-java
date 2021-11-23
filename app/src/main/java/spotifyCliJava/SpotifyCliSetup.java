package spotifyCliJava;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.SpotifyHttpManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spotifyCliJava.authorization.AbstractAuthorizationFlow;
import spotifyCliJava.authorization.AuthManager;
import spotifyCliJava.authorization.flows.AuthorizationFlowClientCredentials;
import spotifyCliJava.authorization.flows.AuthorizationFlowCodeFlow;
import spotifyCliJava.authorization.flows.AuthorizationFlowPKCE;
import spotifyCliJava.authorization.flows.utility.CallbackServer;
import spotifyCliJava.authorization.tokenCaching.SpotifyCliTokenCache;
import spotifyCliJava.utility.Environment;

import java.net.URI;

class SpotifyCliSetup
{
    private static final Logger logger = LoggerFactory.getLogger("spotify-cli-java.spotifyCliJava.SpotifyCliSetup");

    @Nullable
    public static SpotifyApi createAndAuthenticate(@NotNull Environment env){
        // Create and configure a SpotifyApi object
        SpotifyApi spotifyApi = createAndConfigureSpotifyApi(
                env.redirectURI,
                env.clientID,
                env.clientSecret);

        // Create call back server to be used by selected auth flow
        var cbServerBuilder = new CallbackServer.Builder()
                .withHostName(env.callbackServerHostName)
                .withPort(env.callbackServerPort);

        // Create authentication flow, for "authenticating" the spotify instance
        AbstractAuthorizationFlow authFlow = createAuthFlow(
                env.authFlowType,
                env.authScopes,
                spotifyApi,
                cbServerBuilder);

        // Attempt to authenticate
        AuthManager.AuthStatus res = authenticate(
                env.clientSecret,
                env.disableTokenCaching,
                env.disableTokenRefresh,
                authFlow,
                spotifyApi);

        if (res == AuthManager.AuthStatus.FAIL) {
            return null;
        }
        return spotifyApi;
    }

    public static SpotifyApi createAndConfigureSpotifyApi(@NotNull String redirectURI, @NotNull String clientID, String clientSecret){
        // Create SpotifyApi object
        //---------------------------------------------------------------------
        var logMsg = "%s is null. Cannot create spotify session.";
        var userErrorMsg = "ERROR: No configuration found for %s; cannot create Spotify session. " +
                "Please set a configuration using an option/flag, an environment variable, or an .env file";

        // TODO: Add support for logger here?
        if (redirectURI == null || clientID == null) {
            if (redirectURI == null) {
                //logger.error(String.format(logMsg, "Redirect URI"));
                System.err.printf((userErrorMsg) + "%n", "SPOTIFY_REDIRECT_URI");
            }
            if (clientID == null) {
                //logger.error(String.format(logMsg, "Client ID"));
                System.err.printf((userErrorMsg) + "%n", "SPOTIFY_CLIENT_ID");
            }
            System.exit(1);
        }

        final URI spotifyURI = SpotifyHttpManager.makeUri(redirectURI);

        SpotifyApi.Builder spotifyApiBuilder = new SpotifyApi.Builder()
                .setClientId(clientID)
                .setRedirectUri(spotifyURI);

        // Not all authentication flows require a client secret to be set
        if (clientSecret != null)
            spotifyApiBuilder.setClientSecret(clientSecret);

        return spotifyApiBuilder.build();
    }

    @Nullable
    public static AbstractAuthorizationFlow createAuthFlow(
            @NotNull String authFlowType,
            @NotNull String authScopes,
            @NotNull SpotifyApi spotifyApi,
            @NotNull CallbackServer.Builder cbServerBuilder
    ) {
        switch (authFlowType) {
            case "PKCE":
                logger.info("Auth Code with PKCE flow selected");
                var authFlowPKCEBuilder = new AuthorizationFlowPKCE.Builder(spotifyApi, cbServerBuilder)
                        .withShowDialog(false);
                if (authScopes != null)
                    authFlowPKCEBuilder.withScope(authScopes);
                return authFlowPKCEBuilder.build();

            case "CodeFlow":
                logger.info("Auth Code flow selected");
                var authFlowBuilder = new AuthorizationFlowCodeFlow.Builder(spotifyApi, cbServerBuilder)
                        .withShowDialog(false);
                if (authScopes != null)
                    authFlowBuilder.withScope(authScopes);
                return authFlowBuilder.build();

            case "ClientCredentials":
                logger.info("Client Credentials flow selected");
                return new AuthorizationFlowClientCredentials.Builder(spotifyApi, cbServerBuilder)
                        .build();
            default:
                logger.error("No auth flow selected");
                return null;
        }
    }

    /**
     * A static procedure which takes care of authenticating a SpotifyApi object from end to end.
     *
     * Attempt to authenticate the SpotifyApi object using one of the following methods. Listed in the order they will
     * attempted: Loading cached tokens (if enabled), refreshing the cached tokens (if refresh is enabled),
     * requesting a full sign in + approval from the end user.
     *
     * @return AuthStatus enum value indicating if the authentication succeeded (SUCCESS) or failed (FAIL)
     * <p>
     * Attempts to authenticate the SpotifyApi object associated set on current instance
     * using THREE different methods, token caching, token refresh and then
     * finally full user sign in
     *
     * @param spotifyApi
     * @return
     */
    public static AuthManager.AuthStatus authenticate(
            String clientSecret,
            boolean disableTokenCaching,
            boolean disableTokenRefresh,
            AbstractAuthorizationFlow authFlow,
            SpotifyApi spotifyApi) {
        var logMsg = "%s is null. Cannot create spotify session.";
        var userErrorMsg = "ERROR: No configuration found for %s; cannot create Spotify session. " +
                "Please set a configuration using an option/flag, an environment variable, or an .env file";

        // Since not all auth flows require a client secret, check if the current flow DOES require one
        if (clientSecret == null && authFlow.requiresClientSecret()) {
            logger.error(String.format(logMsg, "Client Secret") +
                    "\nCurrent selected auth flow requires client secret to be set");
            System.err.printf((userErrorMsg) + "%n", "SPOTIFY_CLIENT_SECRET");
            System.err.println("Current selected auth flow requires client secret to be set");
            return AuthManager.AuthStatus.FAIL;
        }

        assert authFlow != null;
        var tokenCache = new SpotifyCliTokenCache();
        var authManager = new AuthManager.Builder(authFlow, spotifyApi)
                .withDisableTokenCaching(disableTokenCaching)
                .withDisableTokenRefresh(disableTokenRefresh)
                .withTokenCache(tokenCache)
                .build();

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

        // Try to use tokens from the cache
        if (authManager.authenticateWithTokenCache() == AuthManager.AuthStatus.SUCCESS) {
            return AuthManager.AuthStatus.SUCCESS;
        }

        /*
         * Try to refresh the access token, provided the spotifyCliJava.authorization flow type in use supports token refreshing,
         * token caching is enabled, and the token cache exists.
         *
         * Sets accessToken, refreshToken if successful.
         */
        if (authManager.authenticateWithTokenRefresh() == AuthManager.AuthStatus.SUCCESS) {
            return AuthManager.AuthStatus.SUCCESS;
        }

        // If valid tokens could not be retrieved any other way, require a full refresh
        // This may mean the end user will have to sign in
        if (authManager.authenticateWithFullSignIn() == AuthManager.AuthStatus.SUCCESS) {
            return AuthManager.AuthStatus.SUCCESS;
        }

        return AuthManager.AuthStatus.FAIL;
    }
}

