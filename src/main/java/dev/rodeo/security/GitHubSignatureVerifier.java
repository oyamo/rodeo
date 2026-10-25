package dev.rodeo.security;

import dev.rodeo.config.GitHubWebhookProperties;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class GitHubSignatureVerifier {
    private static final String ALGORITHM = "HmacSHA256";
    private static final String PREFIX = "sha256=";

    private final byte[] secret;

    public GitHubSignatureVerifier(GitHubWebhookProperties properties) {
        this.secret = SecretBytes.required(properties.secret(), "GITHUB_WEBHOOK_SECRET");
    }

    public boolean isValid(byte[] payload, String signatureHeader) {
        if (signatureHeader == null || !signatureHeader.startsWith(PREFIX)) {
            return false;
        }
        byte[] expected = (PREFIX + HexFormat.of().formatHex(hmac(payload)))
                .getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, signatureHeader.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] hmac(byte[] payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return mac.doFinal(payload);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Cannot compute HMAC-SHA256", e);
        }
    }
}
