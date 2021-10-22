import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.SpotifyHttpManager;
import io.github.cdimascio.dotenv.Dotenv;

import java.net.URI;

class SpotifyManager{
    private static Dotenv dotenv;
    private static String SPOTIFY_CLIENT_ID;
    private static String SPOTIFY_CLIENT_SECRET;
    public static String SPOTIFY_REDIRECT_URI;

    // TODO: Convert this into non-static class with an internal builder class
    // I'm allowing users to change the authorization flow used, so this class will have to be able to take
    // optional paramaters in order to allow that

    public static SpotifyApi CreateSession(){

         dotenv = Dotenv.configure()
                 // Don't raise exceptions if .env is missing, or if a var isn't set.
                 // MOST authentication methods don't require CLIENT_SECRET to be set
                .ignoreIfMissing()
                .load();
        SPOTIFY_CLIENT_ID = dotenv.get("SPOTIFY_CLIENT_ID");
        SPOTIFY_CLIENT_SECRET = dotenv.get("SPOTIFY_CLIENT_SECRET");
        SPOTIFY_REDIRECT_URI = dotenv.get("SPOTIFY_REDIRECT_URI");

        // If user did not set ENV vars, or create .env file, use defaults
        // For obvious reasons, there is no default for CLIENT_SECRET
        if (SPOTIFY_CLIENT_ID == null) SPOTIFY_CLIENT_ID = "e896df19119b4105a6e49585b8013bb9";
        if (SPOTIFY_REDIRECT_URI == null) SPOTIFY_REDIRECT_URI = "http://localhost:8080";

        final URI spotifyURI = SpotifyHttpManager.makeUri(SPOTIFY_REDIRECT_URI);

        var spotifyApiBuilder = new SpotifyApi.Builder()
                .setClientId(SPOTIFY_CLIENT_ID)
                .setRedirectUri(spotifyURI);

        if (SPOTIFY_CLIENT_SECRET != null) spotifyApiBuilder.setClientSecret(SPOTIFY_CLIENT_SECRET);

        var spotifyApi = spotifyApiBuilder.build();

        // TODO: Configure auth flow by reading a config file
        // TODO: Maybe scope too?
        // TODO: Might be useful to read a lot of items from an config file
        var authFlow = new AuthorizationFlowPKCE.Builder(spotifyApi)
                //.scope("user-read-birthdate,user-read-email")
                .showDialog(false)
                .build();
        var authManager = new AuthManager(authFlow);
        if (authManager.authenticateSpotifyInstance() == AuthManager.Authentication.FAIL){
            return null;
        }

        return spotifyApi;
    }
}