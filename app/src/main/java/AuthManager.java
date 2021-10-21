import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import com.wrapper.spotify.model_objects.specification.Artist;
import com.wrapper.spotify.requests.data.artists.GetArtistRequest;
import org.apache.hc.core5.http.ParseException;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

class AuthManager {

    public enum CacheState{
        CACHE_DNE,
        ACCESS_TOKEN_EXPIRED,
        ACCESS_TOKEN_VALID,
    }

    // TODO: Change to using actual class types instead of this enum?
    // TODO: Make an abstract base class? interface? For Auth flows?
    public enum AuthorizationFlowType{
        AUTHORIZATION_CODE,
        AUTHORIZATION_CODE_WITH_PKCE,
        IMPLICIT_GRANT,
        CLIENT_CREDENTIALS,
    }

    //TODO: Eliminate hardcoded path string
    private static final String TOKEN_CACHE = "C:\\src\\java-spotify-cli\\app\\token_cache.txt";

    private SpotifyApi spotifyApi;
    private AuthorizerPKCE authorizationFlow; //TODO: Replace with abstract base class type or interface type
    private String accessToken = "";
    private String refreshToken = "";
    private String accessDuration = "NULL";
    private String accessCreationTimeStamp = "NULL";

    // TODO: Use Builder pattern so you can optionally provide scope etc etc
    public AuthManager(SpotifyApi spotifyApi){
        this.spotifyApi = spotifyApi;
        this.authorizationFlow = new AuthorizerPKCE.Builder(spotifyApi)
                //.scope("user-read-birthdate,user-read-email")
                .showDialog(false)
                .build();
    }

    // TODO: Add method for refreshing access token. I THINK I can do that here
    // I think it does not need to be tied to any particular flow
    // NOTE: Whether or not you can refresh the access token DOES depend on the particular flow though so...

    private void fullAuthRefresh(){
        AuthorizationCodeCredentials credentials = authorizationFlow.authorize();

        accessToken = credentials.getAccessToken();
        refreshToken = credentials.getRefreshToken();
        accessDuration = credentials.getExpiresIn().toString();
        // TODO: Add getting current time in HH:MM:SS format
        //this.accessCreationTimeStamp =
        try (var fileWriter = new FileWriter(TOKEN_CACHE)) {
            fileWriter.write("ACCESS_TOKEN\t" + accessToken + "\n");
            fileWriter.write("REFRESH_TOKEN\t" + refreshToken + "\n");
            fileWriter.write("ACCESS_DURATION_SECONDS\t" + accessDuration + "\n");
            fileWriter.write("ACCESS_CREATION_TIMESTAMP\t" + accessCreationTimeStamp + "\n");
            fileWriter.write("ACCESS_CREATION_FORMAT\tHH:MM:SS\n");
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }


    public void authenticateApplication(){
        var cacheState = getCacheState();
        if (cacheState == CacheState.CACHE_DNE) {
            fullAuthRefresh();
        }
        spotifyApi.setAccessToken(this.accessToken);
        // Not all auth flows return a refresh token, namely, Implicit Grand flow and Client Credentials flow
        if (!this.refreshToken.equals("")) {
            spotifyApi.setRefreshToken(this.refreshToken);
        }

        // Test token actually works. ID = Weezer , could probably switch to something else
        GetArtistRequest getArtistRequest = spotifyApi.getArtist("3jOstUTkEu2JkjvRdBA5Gu")
                .build();
        try {
            getArtistRequest.execute();
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            // TODO: Change how we check for invalid token and expired token. Matching vs a string maybe isn't the best
            if (e.getMessage().equals("Invalid access token")){
                System.out.println("Access token was invalid, sign in required so access token can be refreshed");
                fullAuthRefresh();
            }
            if (e.getMessage().equals("The access token expired")){
                // Attempt to use refresh token to get new access token, fall back to fullAuthRefresh if that fails
                // TODO: Implement using refresh token to get new access token
                System.out.println("Access token was expired, sign in required so access token can be refreshed");
                fullAuthRefresh();
            }
        }
    }

    public CacheState getCacheState() {
        // If cache file has not been created yet
        if (Files.notExists(Paths.get(TOKEN_CACHE))) {
            return CacheState.CACHE_DNE;
        }

        try {
            List<String> lines = lines = Files.readAllLines(Paths.get(TOKEN_CACHE));
            accessToken = lines.get(0).split("\t")[1];
            refreshToken = lines.get(1).split("\t")[1];
            accessDuration = lines.get(2).split("\t")[1];
            accessCreationTimeStamp = lines.get(3).split("\t")[1];
        } catch (IOException e) {
            e.printStackTrace();
        }
        // TODO: Implement checking if token is expired based on timestamp
        // If the access token is expired
        // if ( ... convert things to ints, do math ) { return CacheState.ACCESS_TOKEN_EXPIRED; }

        return CacheState.ACCESS_TOKEN_VALID;
    }
}
