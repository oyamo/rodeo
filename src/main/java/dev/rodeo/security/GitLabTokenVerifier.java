package dev.rodeo.security;

import dev.rodeo.config.GitLabWebhookProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;

@Component
public class GitLabTokenVerifier {
    private final byte[] secret;

    public GitLabTokenVerifier(GitLabWebhookProperties properties) {
        this.secret = SecretBytes.required(properties.secret(), "GITLAB_WEBHOOK_SECRET");
    }

    public boolean isValid(String token) {
        if (token == null) {
            return false;
        }
        return MessageDigest.isEqual(secret, token.getBytes(StandardCharsets.UTF_8));
    }
}
