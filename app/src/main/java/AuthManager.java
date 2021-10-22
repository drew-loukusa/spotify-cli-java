import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import com.wrapper.spotify.requests.data.artists.GetArtistRequest;

import kotlin.Pair;
import org.apache.hc.core5.http.ParseException;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

class AuthManager {

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

    //TODO: Eliminate hardcoded path string
    private static final String TOKEN_CACHE = "C:\\src\\java-spotify-cli\\app\\token_cache.txt";

    private SpotifyApi spotifyApi;
    private AbstractAuthorizationFlow authorizationFlow;
    private String accessToken = "";
    private String refreshToken = "";
    private String accessDuration = "NULL";
    private String accessCreationTimeStamp = "NULL";

    public AuthManager(AbstractAuthorizationFlow authorizationFlow){
        this.authorizationFlow = authorizationFlow;
        this.spotifyApi = authorizationFlow.getSpotify();
    }

    // TODO: Add method for refreshing access token. I THINK I can do that here
    // I think it does not need to be tied to any particular flow
    // NOTE: Whether or not you can refresh the access token DOES depend on the particular flow though so...

    // TODO: Switch these print statements over to a logger
    public Authentication authenticateSpotifyInstance(){
        // Try to load tokens from the cache
        var cacheState = loadTokensFromCache();
        if (cacheState == CacheState.ACCESS_TOKEN_VALID) {
            setTokensOnSpotifyInstance();

            //If connection is good, tokens are good
            var pair = testSpotifyConnection();
            if (pair.getFirst()){
                System.out.println("Successfully retrieved access token from cache");
                return Authentication.SUCCESS;
            }

            System.out.println(pair.getSecond());
            cacheState = CacheState.ACCESS_TOKEN_INVALID;
        }
        if(cacheState == CacheState.CACHE_DNE){
            System.out.println("Token cache does not exist");
        }
        else if(cacheState == CacheState.ACCESS_TOKEN_EXPIRED) {
            System.out.println("Cached token was expired");
        }
        else if (cacheState == CacheState.ACCESS_TOKEN_INVALID ){
            System.out.println("Cached token was invalid");
        }

        // Try to refresh the access token, provided that selected auth flow supports refresh and the token cache exists
        if (authorizationFlow.isRefreshable() && cacheState != CacheState.CACHE_DNE ){
            if (authRefresh()) {
                setTokensOnSpotifyInstance();

                var pair = testSpotifyConnection();
                if (pair.getFirst()) {
                    System.out.println("Successfully refreshed access token from Spotify!");
                    return Authentication.SUCCESS;
                }
                System.out.println(pair.getSecond());
            }
        }

        // If valid tokens could not be retrieved any other way, require a full refresh
        // Typically this means the user will have to sign in
        fullAuthRefresh();
        setTokensOnSpotifyInstance();
        var pair = testSpotifyConnection();
        if (pair.getFirst()){
            System.out.println("Successfully retrieved a new access token from Spotify!");
            return Authentication.SUCCESS;
        }

        System.out.println(pair.getSecond());
        return Authentication.FAIL;
    }

    private void cacheTokensToFile(){
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

    private CacheState loadTokensFromCache() {
        // If cache file has not been created yet
        if (Files.notExists(Paths.get(TOKEN_CACHE))) {
            return CacheState.CACHE_DNE;
        }

        try {
            List<String> lines = Files.readAllLines(Paths.get(TOKEN_CACHE));
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

    private boolean authRefresh(){
        AuthorizationCodeCredentials credentials = authorizationFlow.refresh();
        if (credentials == null)
            return false;
        accessToken = credentials.getAccessToken();
        // TODO: What happens when an auth flow doesn't supply a refresh token?
        refreshToken = credentials.getRefreshToken();
        accessDuration = credentials.getExpiresIn().toString();
        // TODO: Add getting current time in HH:MM:SS format
        //this.accessCreationTimeStamp =
        cacheTokensToFile();
        return true;
    }

    private void fullAuthRefresh(){
        AuthorizationCodeCredentials credentials = authorizationFlow.authorize();

        accessToken = credentials.getAccessToken();
        // TODO: What happens when an auth flow doesn't supply a refresh token?
        refreshToken = credentials.getRefreshToken();
        accessDuration = credentials.getExpiresIn().toString();
        // TODO: Add getting current time in HH:MM:SS format
        //this.accessCreationTimeStamp =
        cacheTokensToFile();
    }

    private void setTokensOnSpotifyInstance(){
        spotifyApi.setAccessToken(this.accessToken);
        // Not all auth flows return a refresh token, namely, Implicit Grand flow and Client Credentials flow
        if (!this.refreshToken.equals("")) {
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
