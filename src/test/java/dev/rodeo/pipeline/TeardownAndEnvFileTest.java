package dev.rodeo.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rodeo.config.RodeoProperties;
import dev.rodeo.config.StagesProperties;
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

class TeardownAndEnvFileTest {

    @TempDir
    Path workspace;

    private static final RodeoProperties PROPS = new RodeoProperties(4, true, new StagesProperties(null, "1", "1g"));

    @Test
    void teardownRunsBeforeTheStageAndItsFailureIsIgnored() throws Exception {
        StageSpec stage = stage("deploy",
                List.of("echo deployed > result.txt"),
                List.of("false"),
                null);

        Status status = execute(stage);

        assertThat(status).isEqualTo(Status.SUCCEEDED);
        assertThat(Files.readString(workspace.resolve("result.txt"))).isEqualTo("deployed\n");
    }

    @Test
    void teardownCanStopWhatTheLastRunStarted() throws Exception {
        Files.writeString(workspace.resolve("running.txt"), "old");
        StageSpec stage = stage("deploy",
                List.of("test -f running.txt"),
                List.of("rm -f running.txt"),
                null);

        assertThat(execute(stage)).isEqualTo(Status.FAILED);
        assertThat(Files.exists(workspace.resolve("running.txt"))).isFalse();
    }

    @Test
    void envFileValuesReachTheCommand() throws Exception {
        Files.writeString(workspace.resolve("prod.env"), "# comment\nGREETING=hello\nTARGET = world\n");
        StageSpec stage = stage("deploy",
                List.of("echo \"$GREETING $TARGET\" > out.txt"),
                List.of(),
                "prod.env");

        assertThat(execute(stage)).isEqualTo(Status.SUCCEEDED);
        assertThat(Files.readString(workspace.resolve("out.txt"))).isEqualTo("hello world\n");
    }

    @Test
    void missingEnvFileFailsTheStage() throws Exception {
        StageSpec stage = stage("deploy", List.of("true"), List.of(), "absent.env");

        assertThat(execute(stage)).isEqualTo(Status.FAILED);
    }

    private Status execute(StageSpec stage) {
        CommandRunner runner = new CommandRunner();
        StageExecutor executor = new StageExecutor(
                runner,
                new ContainerRunner(runner, PROPS),
                new SecretsProperties(Map.of()));
        PushEvent event = new PushEvent(
                Provider.GITHUB, "octo/hello", "https://github.com/octo/hello.git", "main", "abc1234", "octocat", 1);
        return executor.execute(stage, new RunContext("tear0001", workspace, event));
    }

    private static StageSpec stage(String name, List<String> run, List<String> teardown, String envFile) {
        return new StageSpec(
                "app",
                name,
                name,
                List.of(),
                Condition.SUCCESS,
                run,
                List.of(),
                Map.of(),
                List.of(),
                null,
                Duration.ofMinutes(1),
                0,
                false,
                null,
                false,
                teardown,
                envFile);
    }
}
