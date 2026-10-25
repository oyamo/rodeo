package dev.rodeo;

import dev.rodeo.config.RodeoConfigFileListener;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RodeoApplication {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(RodeoApplication.class);
        application.addListeners(new RodeoConfigFileListener());
        application.run(args);
    }
}
