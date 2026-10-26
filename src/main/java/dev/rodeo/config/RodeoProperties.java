package dev.rodeo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "rodeo")
public record RodeoProperties(int maxParallelStages, boolean allowHostExecution, StagesProperties stages) {

    @ConstructorBinding
    public RodeoProperties {
        maxParallelStages = maxParallelStages > 0 ? maxParallelStages : Runtime.getRuntime().availableProcessors();
        stages = stages == null ? new StagesProperties(null, null, null) : stages;
    }
}
