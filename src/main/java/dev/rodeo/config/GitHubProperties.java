package dev.rodeo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "github")
public record GitHubProperties(String webhookSecret, String token, String apiUrl, String sshKey)
        implements GitCredentials {

    public GitHubProperties {
        token = blankToNull(token);
        apiUrl = apiUrl == null || apiUrl.isBlank() ? "https://api.github.com" : apiUrl;
        sshKey = blankToNull(sshKey);
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
