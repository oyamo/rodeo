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

    private static final String COMMAND_LINE_SOURCE = "commandLineArgs";
    private static final String PORT = "rodeo.port";
    private static final String SERVER_PORT = "server.port";

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        location(environment).ifPresent(file -> add(environment, file));
    }

    private static void add(ConfigurableEnvironment environment, Path file) {
        if (!Files.exists(file)) {
            throw new IllegalStateException("config file not found: " + file);
        }
        PropertySource<?> source = new MapPropertySource(SOURCE_NAME, load(file));
        if (environment.getPropertySources().contains(COMMAND_LINE_SOURCE)) {
            environment.getPropertySources().addAfter(COMMAND_LINE_SOURCE, source);
        } else {
            environment.getPropertySources().addFirst(source);
        }
    }

    static Optional<Path> location(ConfigurableEnvironment environment) {
        PropertySource<?> arguments = environment.getPropertySources().get(COMMAND_LINE_SOURCE);
        if (arguments == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(arguments.getProperty(ARGUMENT)).map(Object::toString).map(Path::of);
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
        if (properties.containsKey(PORT)) {
            properties.put(SERVER_PORT, properties.remove(PORT));
        }
        return properties;
    }

    private static String qualifiedName(String prefix, String key) {
        if (prefix.isEmpty()) {
            return key;
        }
        return prefix + "." + key;
    }

    private static void flatten(String prefix, TomlTable table, Map<String, Object> out) {
        for (String key : table.keySet()) {
            String name = qualifiedName(prefix, key);
            if (table.isTable(key)) {
                flatten(name, table.getTable(key), out);
            } else {
                out.put(name, String.valueOf(table.get(key)));
            }
        }
    }
}
