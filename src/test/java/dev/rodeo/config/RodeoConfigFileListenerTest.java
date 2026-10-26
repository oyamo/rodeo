package dev.rodeo.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RodeoConfigFileListenerTest {

    @TempDir
    Path directory;

    @Test
    void rodeoPortIsTheServerPort() throws Exception {
        Path file = directory.resolve("config.toml");
        Files.writeString(file, """
                [rodeo]
                port = 9090
                max-parallel-stages = 2

                [rodeo.stages]
                cpus = "2"
                """);

        Map<String, Object> properties = RodeoConfigFileListener.load(file);

        assertThat(properties).containsEntry("server.port", "9090");
        assertThat(properties).doesNotContainKey("rodeo.port");
        assertThat(properties).containsEntry("rodeo.max-parallel-stages", "2");
        assertThat(properties).containsEntry("rodeo.stages.cpus", "2");
    }
}
