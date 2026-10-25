package dev.rodeo.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

public class RodeoConfigFileListener implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    static final String ARGUMENT = "rodeo.config";
    static final String SOURCE_NAME = "rodeo-config-file";

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        Optional<Path> file = location(environment);
        if (file.isEmpty()) {
            return;
        }
        if (!Files.exists(file.get())) {
            throw new IllegalStateException("config file not found: " + file.get());
        }
        Map<String, Object> properties = load(file.get());
        PropertySource<?> source = new MapPropertySource(SOURCE_NAME, properties);
        if (environment.getPropertySources().contains("commandLineArgs")) {
            environment.getPropertySources().addAfter("commandLineArgs", source);
        } else {
            environment.getPropertySources().addFirst(source);
        }
    }

    static Optional<Path> location(ConfigurableEnvironment environment) {
        PropertySource<?> arguments = environment.getPropertySources().get("commandLineArgs");
        Object argument = arguments == null ? null : arguments.getProperty(ARGUMENT);
        return argument == null ? Optional.empty() : Optional.of(Path.of(argument.toString()));
    }

    static Map<String, Object> load(Path file) {
        TomlParseResult result;
        try {
            result = Toml.parse(Files.readString(file));
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + file, e);
        }
        if (result.hasErrors()) {
            throw new IllegalStateException("invalid " + file + ": " + result.errors().getFirst().getMessage());
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        flatten("", result, properties);
        return properties;
    }

    private static void flatten(String prefix, TomlTable table, Map<String, Object> out) {
        for (String key : table.keySet()) {
            String name = prefix.isEmpty() ? key : prefix + "." + key;
            if (table.isTable(key)) {
                flatten(name, table.getTable(key), out);
            } else {
                out.put(name, String.valueOf(table.get(key)));
            }
        }
    }
}
