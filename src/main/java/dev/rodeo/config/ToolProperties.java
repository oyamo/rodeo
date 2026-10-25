package dev.rodeo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "rodeo.tools")
public record ToolProperties(String shell, String docker, String git) {

    @ConstructorBinding
    public ToolProperties {
        shell = shell == null || shell.isBlank() ? "sh" : shell;
        docker = docker == null || docker.isBlank() ? "docker" : docker;
        git = git == null || git.isBlank() ? "git" : git;
    }

    public static ToolProperties defaults() {
        return new ToolProperties(null, null, null);
    }
}
