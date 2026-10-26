package dev.rodeo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "rodeo.exec")
public record ExecProperties(
        int maxParallelStages,
        boolean allowHostExecution,
        String stageCpus,
        String stageMemory,
        String defaultImage) {

    @ConstructorBinding
    public ExecProperties {
        maxParallelStages = maxParallelStages > 0 ? maxParallelStages : Runtime.getRuntime().availableProcessors();
        stageCpus = stageCpus == null || stageCpus.isBlank() ? "1" : stageCpus;
        stageMemory = stageMemory == null || stageMemory.isBlank() ? "1g" : stageMemory;
    }
}
