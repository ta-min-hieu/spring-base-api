package com.ringme.base.security;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.*;
import java.util.Base64;

public class RsaKeyLoader {

    public static PrivateKey loadPrivateKey(String path) {
        try {
            String key = readPem(path)
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");

            byte[] decoded = Base64.getDecoder().decode(key);

            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);

            return KeyFactory.getInstance("RSA").generatePrivate(keySpec);

        } catch (Exception e) {
            throw new RuntimeException("Cannot load private key", e);
        }
    }

    public static PublicKey loadPublicKey(String path) {

        try {

            String key = readPem(path)
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");

            byte[] decoded = Base64.getDecoder().decode(key);

            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);

            return KeyFactory.getInstance("RSA").generatePublic(keySpec);

        } catch (Exception e) {
            throw new RuntimeException("Cannot load public key", e);
        }
    }

    private static String readPem(String path) throws Exception {
        try (InputStream is = RsaKeyLoader.class
                .getClassLoader()
                .getResourceAsStream(path)) {
            if (is == null) {
                throw new RuntimeException("KeyMedia not found: " + path);
            }

            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}