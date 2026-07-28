package com.example.inventory.security;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

public final class RsaTestSupport {

    private final RSAPublicKey publicKey;
    private final RSAPrivateKey privateKey;
    private final Path publicKeyFile;
    private final Path privateKeyFile;

    private RsaTestSupport(RSAPublicKey publicKey, RSAPrivateKey privateKey,
                           Path publicKeyFile, Path privateKeyFile) {
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.publicKeyFile = publicKeyFile;
        this.privateKeyFile = privateKeyFile;
    }

    public static RsaTestSupport create() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            Path publicFile = Files.createTempFile("inventory-jwt-public-", ".pem");
            Path privateFile = Files.createTempFile("inventory-jwt-private-", ".pem");
            Files.writeString(publicFile, pem("PUBLIC KEY", pair.getPublic().getEncoded()),
                    StandardCharsets.US_ASCII);
            Files.writeString(privateFile, pem("PRIVATE KEY", pair.getPrivate().getEncoded()),
                    StandardCharsets.US_ASCII);
            publicFile.toFile().deleteOnExit();
            privateFile.toFile().deleteOnExit();
            return new RsaTestSupport((RSAPublicKey) pair.getPublic(),
                    (RSAPrivateKey) pair.getPrivate(), publicFile, privateFile);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot create ephemeral RSA test keys", exception);
        }
    }

    public NimbusJwtEncoder encoder(SignatureAlgorithm algorithm) {
        return NimbusJwtEncoder.withKeyPair(publicKey, privateKey)
                .algorithm(algorithm)
                .build();
    }

    public String publicKeyLocation() {
        return publicKeyFile.toUri().toString();
    }

    public String privateKeyLocation() {
        return privateKeyFile.toUri().toString();
    }

    private static String pem(String type, byte[] encoded) {
        String body = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded);
        return "-----BEGIN " + type + "-----\n" + body
                + "\n-----END " + type + "-----\n";
    }
}
