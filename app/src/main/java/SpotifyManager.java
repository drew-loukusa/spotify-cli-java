import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.SpotifyHttpManager;
import io.github.cdimascio.dotenv.Dotenv;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

class SpotifyManager {
    private static final Logger logger = LoggerFactory.getLogger("spotify-cli-java.SpotifyManager");

    // TODO: Move defaults out of code, or at least out of THIS Code
    // It might make more sense to have the defaults in the CLI, and have them used if the user provides no
    // config via flags
//    private static final String DEFAULT_CLIENT_ID = "e896df19119b4105a6e49585b8013bb9";
//    private static final String DEFAULT_REDIRECT_URI = "http://localhost:8080";
//    private static final String DEFAULT_AUTH_FLOW = "PKCE";
//    private static final String DEFAULT_DISABLE_TOKEN_CACHING = "false";
//    private static final String DEFAULT_DISABLE_TOKEN_REFRESH = "false";
    private static final String DEFAULT_CLIENT_ID = null;
    private static final String DEFAULT_REDIRECT_URI = null;
    private static final String DEFAULT_AUTH_FLOW = null;
    private static final String DEFAULT_DISABLE_TOKEN_CACHING = null;
    private static final String DEFAULT_DISABLE_TOKEN_REFRESH = null;

    // Don't raise exceptions if .env is missing, or if a var isn't set in the environment;
    // Defaults are provided for Client ID and redirect uri.
    // MOST authentication methods don't require CLIENT_SECRET to be set.
    private static final Dotenv dotenv = Dotenv.configure()
            .ignoreIfMissing()
            .load();

    // These are required, and thus have default values if not set via .env or environment
    private static final String SPOTIFY_AUTH_FLOW = dotenv.get("SPOTIFY_AUTH_FLOW");
    private static final String SPOTIFY_CLIENT_ID = dotenv.get("SPOTIFY_CLIENT_ID");
    private static final String SPOTIFY_REDIRECT_URI = dotenv.get("SPOTIFY_REDIRECT_URI");
    private static final String DISABLE_TOKEN_CACHING = dotenv.get("DISABLE_TOKEN_CACHING");
    private static final String DISABLE_TOKEN_REFRESH = dotenv.get("DISABLE_TOKEN_REFRESH");

    // These are optional
    private static final String SPOTIFY_CLIENT_SECRET = dotenv.get("SPOTIFY_CLIENT_SECRET");
    private static final String SPOTIFY_AUTH_SCOPES = dotenv.get("SPOTIFY_AUTH_SCOPES");

    private final String clientID;
    private final String clientSecret;
    private final String redirectURI;
    private final String authFlowType;
    private final String authScopes;
    private final boolean disableTokenCaching;
    private final boolean disableTokenRefresh;

    private SpotifyManager(Builder builder) {
        // If user did not set ENV vars, or create .env file, use defaults
        // For obvious reasons, there is no default for CLIENT_SECRET
        this.clientID = setVar(
                "SPOTIFY_CLIENT_ID",
                DEFAULT_CLIENT_ID,
                builder.clientID,
                SPOTIFY_CLIENT_ID
        );
        this.clientSecret = setVar(
                "SPOTIFY_CLIENT_SECRET",
                null,
                builder.clientSecret,
                SPOTIFY_CLIENT_SECRET
        );
        this.redirectURI = setVar(
                "SPOTIFY_REDIRECT_URI",
                DEFAULT_REDIRECT_URI,
                builder.redirectURI,
                SPOTIFY_REDIRECT_URI
        );
        this.authFlowType = setVar(
                "SPOTIFY_AUTH_FLOW",
                DEFAULT_AUTH_FLOW,
                builder.authFlowType,
                SPOTIFY_AUTH_FLOW
        );
        this.authScopes = setVar(
                "SPOTIFY_SCOPE",
                null,
                builder.authScopes,
                SPOTIFY_AUTH_SCOPES
        );
        this.disableTokenRefresh = Boolean.parseBoolean(
                setVar(
                        "DISABLE_TOKEN_REFRESH",
                        DEFAULT_DISABLE_TOKEN_REFRESH,
                        String.valueOf(builder.disableTokenRefresh),
                        DISABLE_TOKEN_REFRESH
                )
        );
        this.disableTokenCaching = Boolean.parseBoolean(
                setVar(
                        "DISABLE_TOKEN_CACHING",
                        DEFAULT_DISABLE_TOKEN_CACHING,
                        String.valueOf(builder.disableTokenCaching),
                        DISABLE_TOKEN_CACHING
                )
        );
    }

    /**
     * A method for choosing the "most important value" from a list of values.
     *
     * @param defaultValue Is returned if all vars in 'values' are null
     * @param values       Values are in decreasing order of importance. Earlier values take precedence over later values
     * @return The chosen value.
     */
    private String setVar(String varName, String defaultValue, String... values) {
        String chosenValue = null;
        for (String value : values) {
            if (value != null && !value.equals("null")) {
                chosenValue = value;
                break;
            }
        }
        String message = varName;
        if (chosenValue == null) {
            chosenValue = defaultValue;
            message = message + " (default)";
        }
        message = message + ": " + chosenValue;
        if (chosenValue != null) {
            logger.info(message);
        } else {
            logger.info(varName + ": Not assigned a value");
        }
        return chosenValue;
    }

    @Nullable
    public SpotifyApi CreateSession() {
        var logMsg = "%s is null. Cannot create spotify session.";
        var userErrorMsg = "ERROR: No configuration found for %s; cannot create Spotify session. " +
                "Please set a configuration via a .env file, via an environment variable, or via option/flag.";
        if (redirectURI == null) {
            logger.error(String.format(logMsg, "Redirect URI"));
            System.err.println(String.format(userErrorMsg, "SPOTIFY_REDIRECT_URI"));
            return null;
        }
        final URI spotifyURI = SpotifyHttpManager.makeUri(redirectURI);

        if (clientID == null) {
            logger.error(String.format(logMsg, "Client ID"));
            System.err.println(String.format(userErrorMsg, "SPOTIFY_CLIENT_ID"));
            return null;
        }

        var spotifyApiBuilder = new SpotifyApi.Builder()
                .setClientId(clientID)
                .setRedirectUri(spotifyURI);

        if (clientSecret != null)
            spotifyApiBuilder.setClientSecret(clientSecret);

        var spotifyApi = spotifyApiBuilder.build();

        AbstractAuthorizationFlow authFlow = null;
        // NOTE: More cases coming soon :)
        switch (authFlowType) {
            case "PKCE":
                logger.info("Auth Code with PKCE flow selected");
                var authFlowPKCEBuilder = new AuthFlowPKCE.Builder(spotifyApi)
                        .showDialog(false);
                if (authScopes != null)
                    authFlowPKCEBuilder.scope(authScopes);
                authFlow = authFlowPKCEBuilder.build();
                break;

            case "CodeFlow":
                logger.info("Auth Code flow selected");
                var authFlowBuilder = new AuthFlowCodeFlow.Builder(spotifyApi)
                        .showDialog(false);
                if (authScopes != null)
                    authFlowBuilder.scope(authScopes);
                authFlow = authFlowBuilder.build();
                break;

            case "ClientCredentials":
                logger.info("Client Credentials flow selected");
                authFlow = new AuthFlowClientCredentials.Builder(spotifyApi)
                        .build();
                break;
            default:
                logger.error("No auth flow selected");
        }

        if (clientSecret == null && authFlow.requiresClientSecret()) {
            logger.error(String.format(logMsg, "Client Secret"));
            logger.error("Current selected auth flow requires client secret to be set");
            System.err.println(String.format(userErrorMsg, "SPOTIFY_CLIENT_SECRET"));
            System.err.println("Current selected auth flow requires client secret to be set");
            return null;
        }

        assert authFlow != null;
        var authManager = new AuthManager.Builder(authFlow, spotifyApi)
                .disableTokenCaching(disableTokenCaching)
                .disableTokenRefresh(disableTokenRefresh)
                .build();

        if (authManager.authenticateSpotifyInstance() == AuthManager.Authentication.FAIL) {
            return null;
        }

        return spotifyApi;
    }

    public static class Builder {
        private String clientID;
        private String clientSecret;
        private String redirectURI;
        private String authFlowType;
        private String authScopes;
        private Boolean disableTokenCaching = null;
        private Boolean disableTokenRefresh = null;

        public Builder() {
        }

        public Builder clientID(String clientID) {
            this.clientID = clientID;
            return this;
        }

        public Builder clientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
            return this;
        }

        public Builder redirectURI(String redirectURI) {
            this.redirectURI = redirectURI;
            return this;
        }

        public Builder authScopes(String authScopes) {
            this.authScopes = authScopes;
            return this;
        }

        public Builder authFlowType(String authFlowType) {
            this.authFlowType = authFlowType;
            return this;
        }

        public Builder disableTokenCaching(boolean disableTokenCaching) {
            this.disableTokenCaching = disableTokenCaching;
            return this;
        }

        public Builder disableTokenRefresh(boolean disableTokenRefresh) {
            this.disableTokenRefresh = disableTokenRefresh;
            return this;
        }

        public SpotifyManager build() {
            return new SpotifyManager(this);
        }
    }
}