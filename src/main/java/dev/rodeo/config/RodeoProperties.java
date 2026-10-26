package dev.rodeo.config;

import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "rodeo")
public record RodeoProperties(int maxParallelStages, boolean allowHostExecution, StagesProperties stages) {

    @ConstructorBinding
    public RodeoProperties {
        if (maxParallelStages <= 0) {
            maxParallelStages = Runtime.getRuntime().availableProcessors();
        }
        stages = Objects.requireNonNullElse(stages, new StagesProperties(null, null, null));
    }
}
