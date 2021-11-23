package spotifyCliJava.authorization;

import com.wrapper.spotify.SpotifyApi;
import spotifyCliJava.utility.GenericCredentials;

public interface IAuthorizationFlow {
    GenericCredentials authorize();

    SpotifyApi getSpotify();

    // Can this spotifyCliJava.authorization flow be refreshed?
    // That is, can the access token given by the flow be refreshed?
    boolean isRefreshable();

    GenericCredentials refresh();

    // Does this auth flow require a Spotify Client Secret to run?
    boolean requiresClientSecret();
}