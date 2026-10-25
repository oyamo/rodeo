package dev.rodeo.exec;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class PushExecutorConfig {

    @Bean(destroyMethod = "shutdown")
    ExecutorService pushExecutor() {
        return Executors.newSingleThreadExecutor();
    }
}
