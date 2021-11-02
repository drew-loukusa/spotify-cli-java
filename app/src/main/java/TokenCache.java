import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

interface ITokenCache {
    // Cache the tokens, whether that be to a file, to an in memory store, or database
    void cacheTokens(@NotNull GenericCredentials genericCredentials);

    // Load the tokens from the cache, into a GenericCredentials object and return it
    Pair<TokenCache.CacheState, GenericCredentials> loadTokens();

    // Set the path (if the implementation is using a file based approach) to the token cache
    // Has no effect for non file based token caches
    ITokenCache withTokenCachePath(String tokenCachePath);
}

class TokenCache implements ITokenCache {
    private static final Logger logger = LoggerFactory.getLogger("spotify-cli-java.TokenCache");
    // Provide default, but don't make final so path can be changed by user
    private String tokenCachePath = "token_cache.txt";

    @Override
    public TokenCache withTokenCachePath(String tokenCachePath) {
        this.tokenCachePath = tokenCachePath;
        return this;
    }

    @Override
    public void cacheTokens(@NotNull GenericCredentials genericCredentials) {
        String accessToken = genericCredentials.getAccessToken();
        String refreshToken = genericCredentials.getRefreshToken();
        String accessDuration = genericCredentials.getExpiresIn().toString();
        String accessCreationTimeStamp = genericCredentials.getAccessCreationTimeStamp();
        try (var fileWriter = new FileWriter(tokenCachePath)) {
            fileWriter.write("ACCESS_TOKEN\t" + accessToken
                    + "\nREFRESH_TOKEN\t" + refreshToken
                    + "\nACCESS_DURATION_SECONDS\t" + accessDuration
                    + "\nACCESS_CREATION_TIMESTAMP\t" + accessCreationTimeStamp
                    + "\nACCESS_CREATION_FORMAT\tYY:MM:DD:HH:MM:SS\n"
            );
            logger.info(String.format("Cached tokens to file with name \"%s\"", tokenCachePath));
            logger.debug("Wrote access token: " + accessToken + "\nWrote refresh token: " + refreshToken
                    + "\nWrote access token duration: " + accessDuration
                    + "\nWrote access token timestamp: " + accessCreationTimeStamp
            );
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

    @Override
    public Pair<CacheState, GenericCredentials> loadTokens() {
        // If cache file has not been created yet
        if (Files.notExists(Paths.get(tokenCachePath))) {
            return new Pair<>(CacheState.CACHE_DNE, null);
        }

        GenericCredentials genericCredentials = null;
        try {
            List<String> lines = Files.readAllLines(Paths.get(tokenCachePath));
            String accessToken = lines.get(0).split("\t")[1];
            String refreshToken = lines.get(1).split("\t")[1];
            String accessDuration = lines.get(2).split("\t")[1];
            String accessCreationTimeStamp = lines.get(3).split("\t")[1];
            logger.info(String.format("Loaded tokens from file with name \"%s\"", tokenCachePath));
            logger.debug("Loaded access token: " + accessToken);
            logger.debug("Loaded refresh token: " + refreshToken);
            logger.debug("Loaded access token duration: " + accessDuration);
            logger.debug("Loaded access token timestamp: " + accessCreationTimeStamp);

            genericCredentials = new GenericCredentials.Builder()
                    .withAccessToken(accessToken)
                    .withRefreshToken(refreshToken)
                    .withExpiresIn(Integer.valueOf(accessDuration))
                    .withAccessCreationTimeStamp(accessCreationTimeStamp)
                    .build();

        } catch (IOException e) {
            e.printStackTrace();

        }
        // TODO: Implement checking if token is expired based on timestamp
        // If the access token is expired
        // if ( ... convert things to ints, do math ) { return CacheState.ACCESS_TOKEN_EXPIRED; }

        return new Pair<>(CacheState.ACCESS_TOKEN_VALID, genericCredentials);
    }

    public enum CacheState {
        CACHE_DNE,
        ACCESS_TOKEN_EXPIRED,
        ACCESS_TOKEN_VALID,
        ACCESS_TOKEN_INVALID,
    }
}