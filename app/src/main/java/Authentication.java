import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import com.wrapper.spotify.model_objects.credentials.ClientCredentials;
import com.wrapper.spotify.requests.authorization.authorization_code.AuthorizationCodeUriRequest;
import com.wrapper.spotify.requests.authorization.authorization_code.pkce.AuthorizationCodePKCERefreshRequest;
import com.wrapper.spotify.requests.authorization.authorization_code.pkce.AuthorizationCodePKCERequest;
import com.wrapper.spotify.requests.authorization.client_credentials.ClientCredentialsRequest;
import org.apache.hc.core5.http.ParseException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utility.PKCE;

import java.awt.*;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.security.NoSuchAlgorithmException;

// TODO: Add logging to PKCE (and the other future flows)
// TODO: Add classes for the 1 remaining authorization flow

class GenericCredentials {
    private String accessToken = null;
    private String refreshToken = null;
    private Integer expiresIn = null;
    private GenericCredentials(Builder builder) {
        this.accessToken = builder.accessToken;
        this.refreshToken = builder.refreshToken;
        this.expiresIn = builder.expiresIn;
    }

    public static class Builder {

        private String accessToken;
        private String refreshToken;
        private Integer expiresIn;

        public Builder setAccessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        public Builder setRefreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
            return this;
        }

        public Builder setExpiresIn(Integer expiresIn) {
            this.expiresIn = expiresIn;
            return this;
        }

        public GenericCredentials build() {
            return new GenericCredentials(this);
        }
    }

    public String getAccessToken(){
        return accessToken;
    }

    public String getRefreshToken(){
        return refreshToken;
    }

    public Integer getExpiresIn(){
        return expiresIn;
    }
}

interface IAuthorizationFlow {
    GenericCredentials authorize();
    SpotifyApi getSpotify();

    // Can this authorization flow be refreshed?
    // That is, can the access token given by the flow be refreshed?
    boolean isRefreshable();
    GenericCredentials refresh();
}

abstract class AbstractAuthorizationFlow implements IAuthorizationFlow {
    protected SpotifyApi spotifyApi;

    protected AbstractAuthorizationFlow(final Builder builder) {
        this.spotifyApi = builder.spotifyApi;
    }

    public static abstract class Builder{
        private SpotifyApi spotifyApi;
        protected Builder(SpotifyApi spotifyApi){
            this.spotifyApi = spotifyApi;
        }
    }

    @Override
    public SpotifyApi getSpotify(){
        return spotifyApi;
    }
}

class AuthFlowPKCE extends AbstractAuthorizationFlow {

    private static final Logger logger = (Logger) LoggerFactory.getLogger("spotify-cli-java.AuthFlowPKCE");

    private String codeVerifier;
    private String codeChallenge;
    private String state;
    private String scope;
    private boolean showDialog;

    private AuthFlowPKCE(Builder builder){
        super(builder);
        codeVerifier = builder.codeVerifier;
        codeChallenge = builder.codeChallenge;
        this.state = builder.state;
        this.scope  = builder.scope;
        this.showDialog = builder.showDialog;
    }

    public SpotifyApi getSpotify(){
        return super.spotifyApi;
    }

    // TODO: USE STATE when dealing with code getting, token getting etc. Check the guides etc.
    public static class Builder extends AbstractAuthorizationFlow.Builder {
        private String codeVerifier;
        private String codeChallenge;

        private String state = "";
        private String scope = "";
        private boolean showDialog = false;

        public Builder(@NotNull SpotifyApi spotifyApi){
            super(spotifyApi);
        }

        // Might not need to expose this one to end users, since it might make sense to generate state strings
        // IN the Auth classes for end users... Or maybe leave this here, but provide default state string generation
        public Builder state(String state){
            this.state = state;
            return this;
        }

        public Builder scope(String scope){
            this.scope = scope;
            return this;
        }

        public Builder showDialog(boolean showDialog){
            this.showDialog = showDialog;
            return this;
        }

        public AuthFlowPKCE build(){
            try {
                this.codeVerifier = PKCE.generateCodeVerifier();
                logger.debug("PKCE code verifier generated: " + this.codeVerifier);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

            try {
                this.codeChallenge = PKCE.generateCodeChallenge(this.codeVerifier);
                logger.debug("PKCE code challenge generated: " + this.codeChallenge);
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
            return new AuthFlowPKCE(this);
        }

        protected Builder self() {
            return this;
        }
    }

    @Nullable
    @Override
    public GenericCredentials refresh() {
        AuthorizationCodePKCERefreshRequest authorizationCodePKCERefreshRequest = spotifyApi.authorizationCodePKCERefresh()
                .build();
        AuthorizationCodeCredentials credentials = null;
        try {
            credentials = authorizationCodePKCERefreshRequest.execute();
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            var msg = e.getMessage();
            if (msg.equals("Invalid refresh token")){
                logger.info(msg + ", A full authentication refresh will be required");
            }
            else{
                logger.error(msg);
                e.printStackTrace();
            }
        }
        if (credentials != null) {
            return new GenericCredentials.Builder()
                    .setAccessToken(credentials.getAccessToken())
                    .setRefreshToken(credentials.getRefreshToken())
                    .setExpiresIn(credentials.getExpiresIn())
                    .build();
        }
        else return null;
    }

    @Nullable
    @Override
    public GenericCredentials authorize() {
        AuthorizationCodeUriRequest.Builder requestBuilder = spotifyApi.authorizationCodePKCEUri(codeChallenge)
                .show_dialog(this.showDialog);
        if (!this.state.equals("")) requestBuilder.state(this.state);
        if (!this.scope.equals("")) requestBuilder.scope(this.scope);

        URI uri = requestBuilder.build().execute();

        var cbServer = new CallbackServer();
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            try {
                Desktop.getDesktop().browse(uri);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else{
            System.out.println("Please navigate to this url in a browser and authorize the application:");
            System.out.println("URI: " + uri.toString());
        }
        String authCode = cbServer.getAuthCode();
        cbServer.destroy();

        AuthorizationCodePKCERequest authCodePKCERequest = spotifyApi.authorizationCodePKCE(authCode, codeVerifier)
                .build();

        try {
            AuthorizationCodeCredentials credentials = authCodePKCERequest.execute();
            return new GenericCredentials.Builder()
                    .setAccessToken(credentials.getAccessToken())
                    .setRefreshToken(credentials.getRefreshToken())
                    .setExpiresIn(credentials.getExpiresIn())
                    .build();
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            System.err.println("Error: " + e.getMessage());
        }
        return null;
    }

    @Override
    public boolean isRefreshable(){
        return true;
    }
}

class AuthFlowClientCredentials extends AbstractAuthorizationFlow{

    private AuthFlowClientCredentials(Builder builder){
        super(builder);
    }

    public static class Builder extends AbstractAuthorizationFlow.Builder{
        public Builder(@NotNull SpotifyApi spotifyApi){
            super(spotifyApi);
        }

        public AuthFlowClientCredentials build(){
            return new AuthFlowClientCredentials(this);
        }
    }

    @Override
    public boolean isRefreshable() {
        return false;
    }

    @Override
    public GenericCredentials refresh(){
        return null;
    }

    @Override
    public GenericCredentials authorize(){
        ClientCredentialsRequest clientCredentialsRequest = spotifyApi.clientCredentials().build();

        ClientCredentials credentials = null;
        try {
            credentials = clientCredentialsRequest.execute();
        } catch (IOException | ParseException | SpotifyWebApiException e) {
            e.printStackTrace();
        }

        return new GenericCredentials.Builder()
                .setAccessToken(credentials.getAccessToken())
                .setExpiresIn(credentials.getExpiresIn())
                .build();
    }
}