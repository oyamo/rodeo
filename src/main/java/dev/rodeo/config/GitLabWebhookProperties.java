package dev.rodeo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gitlab.webhook")
public record GitLabWebhookProperties(String secret) {
}
