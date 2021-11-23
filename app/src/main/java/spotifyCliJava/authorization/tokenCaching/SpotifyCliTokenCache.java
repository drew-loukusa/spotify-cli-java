package spotifyCliJava.authorization.tokenCaching;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spotifyCliJava.utility.GenericCredentials;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class SpotifyCliTokenCache implements ITokenCache {
    private static final Logger logger = LoggerFactory.getLogger("spotify-cli-java.spotifyCliJava.utility.TokenCache");
    // Provide default, but don't make final so path can be changed by user
    private String tokenCachePath = "token_cache.txt";

    @Override
    public SpotifyCliTokenCache withTokenCachePath(String tokenCachePath) {
        this.tokenCachePath = tokenCachePath;
        return this;
    }

    @Override
    public void cacheTokens(@NotNull GenericCredentials genericCredentials) {
        String accessToken = genericCredentials.getAccessToken();
        String refreshToken = genericCredentials.getRefreshToken();
        String accessDuration = genericCredentials.getExpiresIn().toString();
        try (var fileWriter = new FileWriter(tokenCachePath)) {
            fileWriter.write("ACCESS_TOKEN\t" + accessToken
                    + "\nREFRESH_TOKEN\t" + refreshToken
                    + "\nACCESS_DURATION_SECONDS\t" + accessDuration
            );
            logger.info(String.format("Cached tokens to file with name \"%s\"", tokenCachePath));
            logger.debug("Wrote access token: " + accessToken + "\nWrote refresh token: " + refreshToken
                    + "\nWrote access token duration: " + accessDuration
            );
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

    @Nullable
    @Override
    public GenericCredentials loadTokens() {
        // If cache file has not been created yet
        if (Files.notExists(Paths.get(tokenCachePath))) {
            return null;
        }

        GenericCredentials genericCredentials = null;
        try {
            List<String> lines = Files.readAllLines(Paths.get(tokenCachePath));
            String accessToken = lines.get(0).split("\t")[1];
            String refreshToken = lines.get(1).split("\t")[1];
            String accessDuration = lines.get(2).split("\t")[1];

            logger.info(String.format("Loaded tokens from file with name \"%s\"", tokenCachePath));
            logger.debug("Loaded access token: " + accessToken
                    + "\nLoaded refresh token: " + refreshToken
                    + "\nLoaded access token duration: " + accessDuration);

            genericCredentials = new GenericCredentials.Builder()
                    .withAccessToken(accessToken)
                    .withRefreshToken(refreshToken)
                    .withExpiresIn(Integer.valueOf(accessDuration))
                    .build();

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return genericCredentials;
    }

    public boolean isValid() {
        if (Files.notExists(Paths.get(tokenCachePath))) {
            logger.info("Token cache does not exist");
            return false;
        }
        // TODO: Implement checking if token is expired based on timestamp
        // If the access token is expired
        // if ( ... convert things to ints, do math ) { return CacheState.ACCESS_TOKEN_EXPIRED; }
        if (false){
            logger.info("Cached token was expired");
            return false;
        }

        return true;
    }
}