package dev.rodeo.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import dev.rodeo.config.RodeoProperties;
import dev.rodeo.config.StagesProperties;
import dev.rodeo.config.Platform;
import dev.rodeo.config.SecretsProperties;
import dev.rodeo.model.Provider;
import dev.rodeo.model.PushEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HostEnvironmentTest {

    @TempDir
    Path workspace;

    @Test
    void hostStagesGetTheDetectedHomeDirectory() throws Exception {
        assumeFalse(Platform.os() == Platform.Os.WINDOWS, "uses POSIX shell syntax");
        RodeoProperties props = new RodeoProperties(4, true, new StagesProperties(null, "1", "1g"));
        CommandRunner runner = new CommandRunner();
        StageExecutor executor = new StageExecutor(
                runner, new ContainerRunner(runner, props), new SecretsProperties(Map.of()));
        StageSpec stage = new StageSpec(
                "app", "home", "home", List.of(), Condition.SUCCESS,
                List.of("echo \"$HOME\" > out.txt"), List.of(), Map.of(), List.of(), null,
                Duration.ofMinutes(1), 0, false, null, false, List.of(), null);
        PushEvent event = new PushEvent(
                Provider.GITHUB, "octo/hello", "https://github.com/octo/hello.git", "main", "abc1234", "octocat", 1);

        Status status = executor.execute(stage, new RunContext("host0001", workspace, event));

        assertThat(status).isEqualTo(Status.SUCCEEDED);
        assertThat(Files.readString(workspace.resolve("out.txt")).strip()).isEqualTo(Platform.homeDirectory());
    }
}
