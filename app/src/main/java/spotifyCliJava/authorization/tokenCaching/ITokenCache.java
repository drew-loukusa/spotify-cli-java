package spotifyCliJava.authorization.tokenCaching;

import org.jetbrains.annotations.NotNull;
import spotifyCliJava.utility.GenericCredentials;

public interface ITokenCache {
    // Cache the tokens, whether that be to a file, to an in memory store, or database
    void cacheTokens(@NotNull GenericCredentials genericCredentials);

    // Load the tokens from the cache, into a spotifyCliJava.utility.GenericCredentials object and return it
    GenericCredentials loadTokens();

    // Is the cache valid? If loadTokens() is called, will it return a valid spotifyCliJava.utility.GenericCredentials object?
    boolean isValid();

    // Set the path (if the implementation is using a file based approach) to the token cache
    // Non file based token caches can simply implement this and return self
    // Returns self, so as  enable flow style programming
    ITokenCache withTokenCachePath(String tokenCachePath);
}