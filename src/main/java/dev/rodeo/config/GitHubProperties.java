package dev.rodeo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "github")
public record GitHubProperties(String webhookSecret, String token, String apiUrl, String sshKey)
        implements GitCredentials {

    private static final String GITHUB_API = "https://api.github.com";

    public GitHubProperties {
        token = Text.blankToNull(token);
        apiUrl = Text.orDefault(apiUrl, GITHUB_API);
        sshKey = Text.blankToNull(sshKey);
    }
}
