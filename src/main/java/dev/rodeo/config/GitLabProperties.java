package dev.rodeo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gitlab")
public record GitLabProperties(String webhookSecret, String token, String sshKey) implements GitCredentials {

    public GitLabProperties {
        token = Text.blankToNull(token);
        sshKey = Text.blankToNull(sshKey);
    }
}
