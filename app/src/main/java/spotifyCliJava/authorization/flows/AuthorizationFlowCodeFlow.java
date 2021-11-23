package spotifyCliJava.authorization.flows;

import spotifyCliJava.utility.GenericCredentials;
import spotifyCliJava.authorization.AbstractAuthorizationFlow;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import com.wrapper.spotify.requests.authorization.authorization_code.AuthorizationCodeRefreshRequest;
import com.wrapper.spotify.requests.authorization.authorization_code.AuthorizationCodeRequest;
import com.wrapper.spotify.requests.authorization.authorization_code.AuthorizationCodeUriRequest;
import org.apache.hc.core5.http.ParseException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spotifyCliJava.authorization.flows.utility.CallbackServer;

import java.awt.*;
import java.io.IOException;
import java.net.URI;

public class AuthorizationFlowCodeFlow extends AbstractAuthorizationFlow {

    private static final Logger logger = LoggerFactory.getLogger("spotify-cli-java.AuthFlowCodeFlow");

    private final String state;
    private final String scope;
    private final boolean showDialog;

    private AuthorizationFlowCodeFlow(Builder builder) {
        super(builder);
        this.state = builder.state;
        this.scope = builder.scope;
        this.showDialog = builder.showDialog;
    }

    public SpotifyApi getSpotify() {
        return super.spotifyApi;
    }

    @Nullable
    @Override
    public GenericCredentials refresh() {
        AuthorizationCodeRefreshRequest authorizationCodeRefreshRequest = spotifyApi.authorizationCodeRefresh()
                .build();
        AuthorizationCodeCredentials credentials = null;
        try {
            credentials = authorizationCodeRefreshRequest.execute();
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            var msg = e.getMessage();
            if (msg.equals("Invalid refresh token") || msg.equals("Refresh token revoked")) {
                msg = msg + ", A full authentication refresh will be required";
                logger.info(msg);
                System.err.println(msg);
            } else {
                logger.error(msg);
                e.printStackTrace();
            }
        }
        if (credentials != null) {
            return new GenericCredentials.Builder()
                    .withAccessToken(credentials.getAccessToken())
                    .withRefreshToken(credentials.getRefreshToken())
                    .withExpiresIn(credentials.getExpiresIn())
                    .build();
        } else return null;
    }

    @Nullable
    @Override
    public GenericCredentials authorize() {
        AuthorizationCodeUriRequest.Builder requestBuilder = spotifyApi.authorizationCodeUri()
                .show_dialog(this.showDialog);
        if (this.state != null) requestBuilder.state(this.state);
        if (this.scope != null) requestBuilder.scope(this.scope);

        URI uri = requestBuilder.build().execute();

        CallbackServer cbServer = cbServerBuilder.build();
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            try {
                Desktop.getDesktop().browse(uri);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("Please navigate to this url in a browser and authorize the application:");
            System.out.println("URI: " + uri.toString());
        }

        String authCode = cbServer.getAuthCode();
        cbServer.destroy();

        AuthorizationCodeRequest authorizationCodeRequest = spotifyApi.authorizationCode(authCode)
                .build();
        try {
            AuthorizationCodeCredentials credentials = authorizationCodeRequest.execute();
            return new GenericCredentials.Builder()
                    .withAccessToken(credentials.getAccessToken())
                    .withRefreshToken(credentials.getRefreshToken())
                    .withExpiresIn(credentials.getExpiresIn())
                    .build();
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            System.err.println("Error: " + e.getMessage());
        }
        return null;
    }

    @Override
    public boolean requiresClientSecret() {
        return true;
    }

    @Override
    public boolean isRefreshable() {
        return true;
    }

    // TODO: USE STATE when dealing with code getting, token getting etc. Check the guides etc.
    public static class Builder extends AbstractAuthorizationFlow.Builder {
        private String state = null;
        private String scope = null;
        private boolean showDialog = false;

        public Builder(@NotNull SpotifyApi spotifyApi, @NotNull CallbackServer.Builder cbServerBuilder) {
            super(spotifyApi, cbServerBuilder);
        }

        // Might not need to expose this one to end users, since it might make sense to generate state strings
        // IN the Auth classes for end users... Or maybe leave this here, but provide default state string generation
        public Builder state(String state) {
            this.state = state;
            return this;
        }

        public Builder withScope(String scope) {
            this.scope = scope;
            return this;
        }

        public Builder withShowDialog(boolean showDialog) {
            this.showDialog = showDialog;
            return this;
        }

        public AuthorizationFlowCodeFlow build() {
            return new AuthorizationFlowCodeFlow(this);
        }
    }
}