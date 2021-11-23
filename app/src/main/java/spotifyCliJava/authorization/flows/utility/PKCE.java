package spotifyCliJava.authorization.flows.utility;
/*
 * The following code is borrowed code that I have modified.
 * The original author is Jaxcskn on GitHub: https://github.com/jaxcksn/nanoleafMusic
 * If this file ever makes it into a repo of mine, I need to add his license or something? He was using BSD 3
 * I think just an acknowledgment in a credits section would be enough... Probably
 */

/*
 * Copyright (c) 2020, Jaxcksn
 * All rights reserved.
 */

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public class PKCE {
    public static String generateCodeVerifier() throws UnsupportedEncodingException {
        SecureRandom secureRandom = new SecureRandom();
        byte[] codeVerifier = new byte[32];
        secureRandom.nextBytes(codeVerifier);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(codeVerifier);
    }
    public static String generateCodeChallenge(String codeVerifier) throws NoSuchAlgorithmException {
        byte[] bytes = codeVerifier.getBytes(StandardCharsets.US_ASCII);
        MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
        messageDigest.update(bytes, 0, bytes.length);
        byte[] digest = messageDigest.digest();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }
}