package dev.rodeo.config;

public record StagesProperties(String defaultImage, String cpus, String memory) {

    public StagesProperties {
        cpus = cpus == null || cpus.isBlank() ? "1" : cpus;
        memory = memory == null || memory.isBlank() ? "1g" : memory;
    }
}
