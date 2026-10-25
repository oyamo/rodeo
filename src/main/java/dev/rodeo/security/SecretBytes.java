package dev.rodeo.security;

import java.nio.charset.StandardCharsets;

final class SecretBytes {
    private SecretBytes() {
    }

    static byte[] required(String secret, String envVariable) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(envVariable + " must be set; refusing to accept unauthenticated webhooks");
        }
        return secret.getBytes(StandardCharsets.UTF_8);
    }
}
