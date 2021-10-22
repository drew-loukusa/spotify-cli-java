import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import com.wrapper.spotify.requests.authorization.authorization_code.AuthorizationCodeUriRequest;

import java.awt.*;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.security.NoSuchAlgorithmException;

import com.wrapper.spotify.requests.authorization.authorization_code.pkce.AuthorizationCodePKCERefreshRequest;
import com.wrapper.spotify.requests.authorization.authorization_code.pkce.AuthorizationCodePKCERequest;
import org.apache.hc.core5.http.ParseException;
import utility.PKCE;

// TODO: Add classes for the 3 other authorization flows
// TODO: Add abstract base class, and interface? for them that they inherit from or implement. Or both

interface IAuthorizationFlow {
    AuthorizationCodeCredentials authorize();
    SpotifyApi getSpotify();

    // Can this authorization flow be refreshed?
    // That is, can the access token given by the flow be refreshed?
    boolean isRefreshable();
    AuthorizationCodeCredentials refresh();
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

class AuthorizationFlowPKCE extends AbstractAuthorizationFlow {

    private String codeVerifier;
    private String codeChallenge;
    private String state;
    private String scope;
    private boolean showDialog;

    private AuthorizationFlowPKCE(Builder builder){
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

        public Builder(SpotifyApi spotifyApi){
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

        public AuthorizationFlowPKCE build(){
            try {
                this.codeVerifier = PKCE.generateCodeVerifier();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

            try {
                this.codeChallenge = PKCE.generateCodeChallenge(this.codeVerifier);
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
            return new AuthorizationFlowPKCE(this);
        }

        protected Builder self() {
            return this;
        }
    }

    @Override
    public AuthorizationCodeCredentials refresh() {
        AuthorizationCodePKCERefreshRequest authorizationCodePKCERefreshRequest = spotifyApi.authorizationCodePKCERefresh()
                .build();
        AuthorizationCodeCredentials codeCredentials = null;
        try {
            codeCredentials = authorizationCodePKCERefreshRequest.execute();
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            //e.printStackTrace();
            System.out.println(e.getMessage());
        }
        return codeCredentials;
    }

    @Override
    public AuthorizationCodeCredentials authorize() {
        AuthorizationCodeUriRequest.Builder requestBuilder = spotifyApi.authorizationCodePKCEUri(codeChallenge)
                .show_dialog(this.showDialog);
        if (this.state != "") requestBuilder.state(this.state);
        if (this.scope != "") requestBuilder.scope(this.scope);

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
            return authCodePKCERequest.execute();
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
