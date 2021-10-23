import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.SpotifyHttpManager;
import io.github.cdimascio.dotenv.Dotenv;

import java.net.URI;

class SpotifyManager{

    private static final String DEFAULT_CLIENT_ID = "e896df19119b4105a6e49585b8013bb9";
    private static final String DEFAULT_REDIRECT_URI = "http://localhost:8080";

    private String authFlowType;
    private String scope;
    private boolean disableTokenCaching;
    private boolean disableRefresh;

    private SpotifyManager(Builder builder){
        this.authFlowType = builder.authFlowType;
        this.scope = builder.scope;
        this.disableTokenCaching = builder.disableTokenCaching;
        this.disableRefresh = builder.disableRefresh;
    }

    public static class Builder {
        private String authFlowType = "PKCE";
        private String scope = "";
        private boolean disableTokenCaching = false;
        private boolean disableRefresh = false;

        public Builder(){}

        /**
         * @param authFlowType
         * DEFAULT: PKCE
         */
        public Builder authFlowType(String authFlowType) {
            this.authFlowType = authFlowType;
            return this;
        }

        public Builder scope(String scope) {
            this.scope = scope;
            return this;
        }

        public Builder disableTokenCaching(boolean disableTokenCaching) {
            this.disableTokenCaching = disableTokenCaching;
            return this;
        }

        public Builder disableRefresh(boolean disableRefresh) {
            this.disableRefresh = disableRefresh;
            return this;
        }

        public SpotifyManager build(){
            return new SpotifyManager(this);
        }
    }

    public SpotifyApi CreateSession(){

         var dotenv = Dotenv.configure()
                 // Don't raise exceptions if .env is missing, or if a var isn't set.
                 // MOST authentication methods don't require CLIENT_SECRET to be set
                .ignoreIfMissing()
                .load();
        String SPOTIFY_CLIENT_ID = dotenv.get("SPOTIFY_CLIENT_ID");
        String SPOTIFY_CLIENT_SECRET = dotenv.get("SPOTIFY_CLIENT_SECRET");
        String SPOTIFY_REDIRECT_URI = dotenv.get("SPOTIFY_REDIRECT_URI");

        // If user did not set ENV vars, or create .env file, use defaults
        // For obvious reasons, there is no default for CLIENT_SECRET
        if (SPOTIFY_CLIENT_ID == null) SPOTIFY_CLIENT_ID = DEFAULT_CLIENT_ID;
        if (SPOTIFY_REDIRECT_URI == null) SPOTIFY_REDIRECT_URI = DEFAULT_REDIRECT_URI;

        final URI spotifyURI = SpotifyHttpManager.makeUri(SPOTIFY_REDIRECT_URI);

        var spotifyApiBuilder = new SpotifyApi.Builder()
                .setClientId(SPOTIFY_CLIENT_ID)
                .setRedirectUri(spotifyURI);

        if (SPOTIFY_CLIENT_SECRET != null) spotifyApiBuilder.setClientSecret(SPOTIFY_CLIENT_SECRET);

        var spotifyApi = spotifyApiBuilder.build();

        // TODO: Configure auth flow by reading a config file
        // TODO: Maybe scope too?
        // TODO: Might be useful to read a lot of items from an config file

        AuthorizationFlowPKCE authFlow = null;
        // NOTE: More cases coming soon :)
        switch (authFlowType){
            case "PKCE":
                authFlow = new AuthorizationFlowPKCE.Builder(spotifyApi)
                        //.scope("user-read-birthdate,user-read-email")
                        .showDialog(false)
                        .build();
            default:
               System.out.println("ERROR: No auth flow selected");
        }

        var authManager = new AuthManager.Builder(authFlow, spotifyApi)
                //.disableTokenCaching()
                //.disableRefresh()
                .build();

        if (authManager.authenticateSpotifyInstance() == AuthManager.Authentication.FAIL){
            return null;
        }

        return spotifyApi;
    }
}