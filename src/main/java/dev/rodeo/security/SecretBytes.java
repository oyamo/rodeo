package dev.rodeo.security;

import java.nio.charset.StandardCharsets;

final class SecretBytes {

    private SecretBytes() {
    }

    static byte[] required(String secret, String property) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(property + " must be set in the config file given with --rodeo.config; refusing to accept unauthenticated webhooks");
        }
        return secret.getBytes(StandardCharsets.UTF_8);
    }
}
