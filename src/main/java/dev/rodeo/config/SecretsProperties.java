package dev.rodeo.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rodeo.secrets")
public record SecretsProperties(Map<String, String> values) {

    public SecretsProperties {
        values = values == null ? Map.of() : Map.copyOf(values);
    }
}
