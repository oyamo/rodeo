package dev.rodeo.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rodeo.exec")
public record ExecProperties(int maxParallelStages, List<String> allowedSecrets) {

    public ExecProperties {
        maxParallelStages = maxParallelStages > 0 ? maxParallelStages : 4;
        allowedSecrets = allowedSecrets == null
                ? List.of()
                : allowedSecrets.stream().filter(secret -> !secret.isBlank()).toList();
    }
}
