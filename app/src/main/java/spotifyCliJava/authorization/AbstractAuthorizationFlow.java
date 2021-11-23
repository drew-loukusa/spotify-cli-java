package spotifyCliJava.authorization;

import com.wrapper.spotify.SpotifyApi;
import spotifyCliJava.authorization.flows.utility.CallbackServer;

// TODO: Add name field to abstract auth flow
public abstract class AbstractAuthorizationFlow implements IAuthorizationFlow {
    protected SpotifyApi spotifyApi;
    protected CallbackServer.Builder cbServerBuilder;

    protected AbstractAuthorizationFlow(final Builder builder) {
        this.spotifyApi = builder.spotifyApi;
        this.cbServerBuilder = builder.cbServerBuilder;
    }

    @Override
    public SpotifyApi getSpotify() {
        return spotifyApi;
    }

    public static abstract class Builder {
        private final SpotifyApi spotifyApi;
        private final CallbackServer.Builder cbServerBuilder;

        protected Builder(SpotifyApi spotifyApi, CallbackServer.Builder cbServerBuilder) {
            this.spotifyApi = spotifyApi;
            this.cbServerBuilder = cbServerBuilder;
        }
    }
}