import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import com.wrapper.spotify.requests.authorization.authorization_code.AuthorizationCodeUriRequest;

import java.awt.*;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.security.NoSuchAlgorithmException;

import com.wrapper.spotify.requests.authorization.authorization_code.pkce.AuthorizationCodePKCERequest;
import org.apache.hc.core5.http.ParseException;
import utility.PKCE;

class AuthUtil {
    public static AuthorizerPKCE.Builder AuthenticateWithPKCE(SpotifyApi spotifyApi){
        return new AuthorizerPKCE.Builder(spotifyApi);
    }
}

class AuthorizerPKCE {

    private String codeVerifier;
    private String codeChallenge;

    private SpotifyApi spotifyApi;
    private String state;
    private String scope;
    private boolean showDialog;

    private AuthorizerPKCE(Builder builder){
        codeVerifier = builder.codeVerifier;
        codeChallenge = builder.codeChallenge;
        this.spotifyApi = builder.spotifyApi;
        this.state = builder.state;
        this.scope  = builder.scope;
        this.showDialog = builder.showDialog;
    }

    // TODO: Make sure you handle STATE when dealing with code getting, token getting etc. Check the guides etc.
    public static class Builder {
        private String codeVerifier;
        private String codeChallenge;

        private SpotifyApi spotifyApi;
        private String state = "";
        private String scope = "";
        private boolean showDialog = false;

        public Builder(SpotifyApi spotifyApi){
            this.spotifyApi = spotifyApi;
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

        public AuthorizerPKCE build(){
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
            return new AuthorizerPKCE(this);
        }

        protected Builder self() {
            return this;
        }
    }

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
        System.err.println("AUTH CODE: " + authCode);
        cbServer.destroy();

        AuthorizationCodePKCERequest authCodePKCERequest = spotifyApi.authorizationCodePKCE(authCode, codeVerifier)
                .build();

        try {
            AuthorizationCodeCredentials authCodeCredentials = authCodePKCERequest.execute();

            // Set access and refresh token for further "spotifyApi" object usage
            System.out.println("=================================================================");
            System.out.println("Access token: " + authCodeCredentials.getAccessToken());
            System.out.println("Refresh token: " + authCodeCredentials.getRefreshToken());
            System.out.println("=================================================================");
            spotifyApi.setAccessToken(authCodeCredentials.getAccessToken());
            spotifyApi.setRefreshToken(authCodeCredentials.getRefreshToken());
            System.err.println("Expires in: " + authCodeCredentials.getExpiresIn());

            return authCodeCredentials;
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            System.err.println("Error: " + e.getMessage());
        }
        return null;
    }
}
