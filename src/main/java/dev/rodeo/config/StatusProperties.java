package dev.rodeo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rodeo.status")
public record StatusProperties(String githubToken, String githubApiUrl) {

    public StatusProperties {
        githubToken = githubToken == null ? "" : githubToken.strip();
        githubApiUrl = githubApiUrl == null || githubApiUrl.isBlank() ? "https://api.github.com" : githubApiUrl;
    }
}
