package dev.rodeo.config;

import java.util.Map;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rodeo.secrets")
public record SecretsProperties(Map<String, String> values) {

    public SecretsProperties {
        values = Map.copyOf(Objects.requireNonNullElse(values, Map.of()));
    }
}
