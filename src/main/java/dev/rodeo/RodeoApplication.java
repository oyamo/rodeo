package dev.rodeo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RodeoApplication {
    public static void main(String[] args) {
        SpringApplication.run(RodeoApplication.class, args);
    }
}
