import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A class for managing the environment of the Spotify CLI.
 * This is essentially a class which, upon instantiation, initializes the class fields according to some precedence rules.
 *
 * The precedence is as follows:
 * (1). Command line arguments (arguments passed in via the builder); These override everything else
 * (2). Environment variables; These only override values set in a .env file
 * (3). Vars set in a .env file; These are the lowest in terms of precedence
 */
class Environment {
    private static final Logger logger = LoggerFactory.getLogger("spotify-cli-java.Environment");

    // TODO: Maybe move defaults out of code, or at least out of THIS Code
    // I can override the defaults in other places, so maybe this is fine? not Sure yet
    // It might make more sense to have the defaults in the CLI, and have them used if the user provides no
    // config via flags
    private static final String DEFAULT_CLIENT_ID = "e896df19119b4105a6e49585b8013bb9";
    private static final String DEFAULT_REDIRECT_URI = "http://localhost:8080";
    private static final String DEFAULT_AUTH_FLOW = "PKCE";
    private static final String DEFAULT_DISABLE_TOKEN_CACHING = "false";
    private static final String DEFAULT_DISABLE_TOKEN_REFRESH = "false";

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
    private static final String SPOTIFY_AUTH_SCOPES = dotenv.get("SPOTIFY_AUTH_SCOPES").replace(',', ' ');;

    public final String clientID;
    public final String clientSecret;
    public final String redirectURI;
    public final String authFlowType;
    public final String authScopes;
    public final boolean disableTokenCaching;
    public final boolean disableTokenRefresh;
    public String callbackServerHostName;
    public int callbackServerPort;

    private Environment(Builder builder) {
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
                "SPOTIFY_AUTH_SCOPES",
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

        //TODO: If no port is specified via the redirect URI, what happens?
        var tokens = this.redirectURI.split(":");
        if (tokens.length > 1) {
            if (tokens.length == 3)
                this.callbackServerPort = Integer.parseInt(tokens[2]);
            this.callbackServerHostName = tokens[1].substring(2);

            logger.info("CALLBACK SERVER PORT: " + callbackServerPort);
            logger.info("CALLBACK SERVER HOST NAME: " + callbackServerHostName);
        }
        else {
            logger.error("Cannot parse callback server host or port, redirect URI is invalid: " + redirectURI);
        }
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
            logger.info(varName + ": <No assigned value>");
        }
        return chosenValue;
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

        public Builder withClientID(String clientID) {
            this.clientID = clientID;
            return this;
        }

        public Builder withClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
            return this;
        }

        public Builder withRedirectURI(String redirectURI) {
            this.redirectURI = redirectURI;
            return this;
        }

        public Builder withAuthScopes(String authScopes) {
            this.authScopes = authScopes;
            return this;
        }

        public Builder withAuthFlowType(String authFlowType) {
            this.authFlowType = authFlowType;
            return this;
        }

        public Builder withDisableTokenCaching(boolean disableTokenCaching) {
            this.disableTokenCaching = disableTokenCaching;
            return this;
        }

        public Builder withDisableTokenRefresh(boolean disableTokenRefresh) {
            this.disableTokenRefresh = disableTokenRefresh;
            return this;
        }

        public Environment build() {
            return new Environment(this);
        }
    }
}