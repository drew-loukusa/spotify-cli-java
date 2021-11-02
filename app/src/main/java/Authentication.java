import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import com.wrapper.spotify.model_objects.credentials.ClientCredentials;
import com.wrapper.spotify.requests.authorization.authorization_code.AuthorizationCodeRefreshRequest;
import com.wrapper.spotify.requests.authorization.authorization_code.AuthorizationCodeRequest;
import com.wrapper.spotify.requests.authorization.authorization_code.AuthorizationCodeUriRequest;
import com.wrapper.spotify.requests.authorization.authorization_code.pkce.AuthorizationCodePKCERefreshRequest;
import com.wrapper.spotify.requests.authorization.authorization_code.pkce.AuthorizationCodePKCERequest;
import com.wrapper.spotify.requests.authorization.client_credentials.ClientCredentialsRequest;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.ParseException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utility.CallbackServer;
import utility.PKCE;

import javax.print.attribute.standard.Destination;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;


interface IAuthorizationFlow {
    GenericCredentials authorize();

    SpotifyApi getSpotify();

    // Can this authorization flow be refreshed?
    // That is, can the access token given by the flow be refreshed?
    boolean isRefreshable();

    GenericCredentials refresh();

    // Does this auth flow require a Spotify Client Secret to run?
    boolean requiresClientSecret();
}

class GenericCredentials {
    private final String accessToken;
    private final String refreshToken;
    private final Integer expiresIn;
    private final String accessCreationTimeStamp;

    private GenericCredentials(Builder builder) {
        this.accessToken = builder.accessToken;
        this.refreshToken = builder.refreshToken;
        this.expiresIn = builder.expiresIn;
        this.accessCreationTimeStamp = builder.accessCreationTimeStamp;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public Integer getExpiresIn() {
        return expiresIn;
    }

    public String getAccessCreationTimeStamp() { return accessCreationTimeStamp; }

    public static String getTimeStamp(){
        LocalDateTime now = LocalDateTime.now();
        int year = now.getYear();
        int month = now.getMonthValue();
        int day = now.getDayOfMonth();
        int hour = now.getHour();
        int minute = now.getMinute();
        int second = now.getSecond();
        return String.format("%d-%02d-%02d %02d:%02d:%02d", year, month, day, hour, minute, second);
    }

    public static class Builder {

        private String accessToken;
        private String refreshToken;
        private Integer expiresIn;
        private String accessCreationTimeStamp = getTimeStamp();

        public Builder withAccessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        public Builder withRefreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
            return this;
        }

        public Builder withExpiresIn(Integer expiresIn) {
            this.expiresIn = expiresIn;
            return this;
        }

        /**
         * NOTE: accessCreationTimeStamp is AUTOMATICALLY set by the class when a GenericCredentials object is created.
         * You can override with your own timestamp, but it has to be in the following format:
         * FORMAT: %d-%02d-%02d %02d:%02d:%02d
         * EXAMPLE DATE: 2021-10-29 14:02:16
         */
        public Builder withAccessCreationTimeStamp(String accessCreationTimeStamp){
            // TODO: validate format of string here, raise exception?
            this.accessCreationTimeStamp = accessCreationTimeStamp;
            return this;
        }

        public GenericCredentials build() {
            return new GenericCredentials(this);
        }
    }
}

// TODO: Add name field to abstract auth flow
abstract class AbstractAuthorizationFlow implements IAuthorizationFlow {
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

class AuthFlowCodeFlow extends AbstractAuthorizationFlow {

    private static final Logger logger = LoggerFactory.getLogger("spotify-cli-java.AuthFlowCodeFlow");

    private final String state;
    private final String scope;
    private final boolean showDialog;

    private AuthFlowCodeFlow(Builder builder) {
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

        public AuthFlowCodeFlow build() {
           return new AuthFlowCodeFlow(this);
        }
    }
}

class AuthFlowPKCE extends AbstractAuthorizationFlow {

    private static final Logger logger = LoggerFactory.getLogger("spotify-cli-java.AuthFlowPKCE");

    private final String codeVerifier;
    private final String codeChallenge;
    private final String state;
    private final String scope;
    private final boolean showDialog;

    private AuthFlowPKCE(Builder builder) {
        super(builder);
        codeVerifier = builder.codeVerifier;
        codeChallenge = builder.codeChallenge;
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
        AuthorizationCodePKCERefreshRequest authorizationCodePKCERefreshRequest = spotifyApi.authorizationCodePKCERefresh()
                .build();
        AuthorizationCodeCredentials credentials = null;
        try {
            credentials = authorizationCodePKCERefreshRequest.execute();
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
        AuthorizationCodeUriRequest.Builder requestBuilder = spotifyApi.authorizationCodePKCEUri(codeChallenge)
                .show_dialog(this.showDialog);
        if (this.state != null) requestBuilder.state(this.state);
        if (this.state != null) requestBuilder.scope(this.scope);

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

        AuthorizationCodePKCERequest authCodePKCERequest = spotifyApi.authorizationCodePKCE(authCode, codeVerifier)
                .build();

        try {
            AuthorizationCodeCredentials credentials = authCodePKCERequest.execute();
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
        return false;
    }

    @Override
    public boolean isRefreshable() {
        return true;
    }

    // TODO: USE STATE when dealing with code getting, token getting etc. Check the guides etc.
    public static class Builder extends AbstractAuthorizationFlow.Builder {
        private String codeVerifier;
        private String codeChallenge;

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

        public AuthFlowPKCE build() {
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
    }
}

class AuthFlowClientCredentials extends AbstractAuthorizationFlow {

    private AuthFlowClientCredentials(Builder builder) {
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

        public AuthFlowClientCredentials build() {
            return new AuthFlowClientCredentials(this);
        }
    }
}