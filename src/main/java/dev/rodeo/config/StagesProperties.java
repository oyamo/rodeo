package dev.rodeo.config;

public record StagesProperties(String defaultImage, String cpus, String memory) {

    private static final String DEFAULT_CPUS = "1";
    private static final String DEFAULT_MEMORY = "1g";

    public StagesProperties {
        cpus = Text.orDefault(cpus, DEFAULT_CPUS);
        memory = Text.orDefault(memory, DEFAULT_MEMORY);
    }
}
