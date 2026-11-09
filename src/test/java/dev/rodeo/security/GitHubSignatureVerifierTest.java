package dev.rodeo.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rodeo.config.GitHubProperties;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class GitHubSignatureVerifierTest {
    private static final String SECRET = "test-secret";

    @Test
    void validSignatureIsAccepted() {
        GitHubSignatureVerifier verifier = new GitHubSignatureVerifier(new GitHubProperties(SECRET, null, null, null));
        byte[] payload = "test payload".getBytes(StandardCharsets.UTF_8);
        String signature = createSignature(SECRET, payload);

        assertThat(verifier.isValid(payload, signature)).isTrue();
    }

    @Test
    void invalidSignatureIsRejected() {
        GitHubSignatureVerifier verifier = new GitHubSignatureVerifier(new GitHubProperties(SECRET, null, null, null));
        byte[] payload = "test payload".getBytes(StandardCharsets.UTF_8);

        assertThat(verifier.isValid(payload, "sha256=invalid")).isFalse();
    }

    @Test
    void nullSignatureIsRejected() {
        GitHubSignatureVerifier verifier = new GitHubSignatureVerifier(new GitHubProperties(SECRET, null, null, null));
        byte[] payload = "test payload".getBytes(StandardCharsets.UTF_8);

        assertThat(verifier.isValid(payload, null)).isFalse();
    }

    @Test
    void signatureWithoutPrefixIsRejected() {
        GitHubSignatureVerifier verifier = new GitHubSignatureVerifier(new GitHubProperties(SECRET, null, null, null));
        byte[] payload = "test payload".getBytes(StandardCharsets.UTF_8);
        String signature = createSignature(SECRET, payload);
        String withoutPrefix = signature.substring("sha256=".length());

        assertThat(verifier.isValid(payload, withoutPrefix)).isFalse();
    }

    @Test
    void tamperedPayloadIsDetected() {
        GitHubSignatureVerifier verifier = new GitHubSignatureVerifier(new GitHubProperties(SECRET, null, null, null));
        byte[] payload = "test payload".getBytes(StandardCharsets.UTF_8);
        String signature = createSignature(SECRET, payload);
        byte[] tamperedPayload = "test payload tampered".getBytes(StandardCharsets.UTF_8);

        assertThat(verifier.isValid(tamperedPayload, signature)).isFalse();
    }

    @Test
    void emptyPayloadWithValidSignatureIsAccepted() {
        GitHubSignatureVerifier verifier = new GitHubSignatureVerifier(new GitHubProperties(SECRET, null, null, null));
        byte[] payload = new byte[0];
        String signature = createSignature(SECRET, payload);

        assertThat(verifier.isValid(payload, signature)).isTrue();
    }

    private static String createSignature(String secret, byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload);
            return "sha256=" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }
}
