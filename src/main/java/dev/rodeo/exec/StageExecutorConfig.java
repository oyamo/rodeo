package dev.rodeo.exec;

import dev.rodeo.config.ExecProperties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class StageExecutorConfig {

    @Bean(destroyMethod = "shutdown")
    ExecutorService stagePool(ExecProperties properties) {
        return Executors.newFixedThreadPool(properties.maxParallelStages());
    }
}
