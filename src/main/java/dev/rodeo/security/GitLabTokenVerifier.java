package dev.rodeo.security;

import dev.rodeo.config.GitLabProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;

@Component
public class GitLabTokenVerifier {
    private final byte[] secret;

    public GitLabTokenVerifier(GitLabProperties properties) {
        this.secret = SecretBytes.required(properties.webhookSecret(), "gitlab.webhook-secret");
    }

    public boolean isValid(String token) {
        if (token == null) {
            return false;
        }
        return MessageDigest.isEqual(secret, token.getBytes(StandardCharsets.UTF_8));
    }
}
