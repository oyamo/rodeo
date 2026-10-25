package dev.rodeo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "github.webhook")
public record GitHubWebhookProperties(String secret) {
}
