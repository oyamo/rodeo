package dev.rodeo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "rodeo.exec")
public record ExecProperties(
        int maxParallelStages,
        boolean allowHostExecution,
        String stageCpus,
        String stageMemory,
        String path,
        String home) {

    @ConstructorBinding
    public ExecProperties {
        maxParallelStages = maxParallelStages > 0 ? maxParallelStages : 4;
        stageCpus = stageCpus == null || stageCpus.isBlank() ? "1" : stageCpus;
        stageMemory = stageMemory == null || stageMemory.isBlank() ? "1g" : stageMemory;
        path = path == null || path.isBlank() ? "/usr/local/bin:/usr/bin:/bin" : path;
        home = home == null || home.isBlank() ? "/var/lib/rodeo" : home;
    }
}
