package spotifyCliJava.authorization.flows;

import spotifyCliJava.utility.GenericCredentials;
import spotifyCliJava.authorization.AbstractAuthorizationFlow;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.credentials.ClientCredentials;
import com.wrapper.spotify.requests.authorization.client_credentials.ClientCredentialsRequest;
import org.apache.hc.core5.http.ParseException;
import org.jetbrains.annotations.NotNull;
import spotifyCliJava.authorization.flows.utility.CallbackServer;

import java.io.IOException;

public class AuthorizationFlowClientCredentials extends AbstractAuthorizationFlow {

    private AuthorizationFlowClientCredentials(Builder builder) {
        super(builder);
    }

    @Override
    public boolean requiresClientSecret() {
        return true;
    }

    @Override
    public boolean isRefreshable() {
        return false;
    }

    @Override
    public GenericCredentials refresh() {
        return null;
    }

    @Override
    public GenericCredentials authorize() {
        ClientCredentialsRequest clientCredentialsRequest = spotifyApi.clientCredentials().build();

        ClientCredentials credentials = null;
        try {
            credentials = clientCredentialsRequest.execute();
        } catch (IOException | ParseException | SpotifyWebApiException e) {
            e.printStackTrace();
        }

        return new GenericCredentials.Builder()
                .withAccessToken(credentials.getAccessToken())
                .withExpiresIn(credentials.getExpiresIn())
                .build();
    }

    public static class Builder extends AbstractAuthorizationFlow.Builder {
        public Builder(@NotNull SpotifyApi spotifyApi, @NotNull CallbackServer.Builder cbServerBuilder) {
            super(spotifyApi, cbServerBuilder);
        }

        public AuthorizationFlowClientCredentials build() {
            return new AuthorizationFlowClientCredentials(this);
        }
    }
}