package dev.rodeo.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PlatformTest {

    @Test
    void homeIsTheServiceAccountHome() {
        assertThat(Platform.home()).isEqualTo(Path.of(System.getProperty("user.home")));
    }

    @Test
    void pathOnlyListsDirectoriesThatExist() {
        for (Path directory : Platform.searchDirectories()) {
            assertThat(Files.isDirectory(directory)).isTrue();
        }
    }

    @Test
    void windowsPassesSystemRootToHostCommands() {
        boolean windows = Platform.os() == Platform.Os.WINDOWS;

        assertThat(Platform.environment().containsKey("SystemRoot")).isEqualTo(windows);
    }

    @Test
    void shellIsDetectedForThisPlatform() {
        assertThat(Platform.shell()).isNotBlank();
        String expected = "-c";
        if (Platform.os() == Platform.Os.WINDOWS) {
            expected = "-Command";
        }
        assertThat(Platform.shellFlag()).isEqualTo(expected);
    }

    @Test
    void maxParallelStagesDefaultsToTheCpuCount() {
        RodeoProperties props = new RodeoProperties(0, false, new StagesProperties(null, null, null));

        assertThat(props.maxParallelStages()).isEqualTo(Runtime.getRuntime().availableProcessors());
    }
}
