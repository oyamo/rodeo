package dev.rodeo.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.rodeo.config.ExecProperties;
import dev.rodeo.config.ToolProperties;
import dev.rodeo.config.SecretsProperties;
import dev.rodeo.model.Provider;
import dev.rodeo.model.PushEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DockerIsolationTest {

    private static final String IMAGE = "alpine:3.20";

    @TempDir
    Path tempDir;

    @BeforeAll
    static void requireDocker() throws Exception {
        assumeTrue(run("docker", "info") == 0, "Docker daemon is not reachable");
        assumeTrue(run("docker", "pull", IMAGE) == 0, "could not pull " + IMAGE);
    }

    @Test
    void hostFilesAreNotVisibleInsideTheContainer() throws Exception {
        Path hostSecret = Files.writeString(tempDir.resolve("host-secret.txt"), "do-not-read");
        Path workspace = Files.createDirectory(tempDir.resolve("workspace"));

        Status status = execute(workspace, stage("read-host", "cat '" + hostSecret + "'", false));

        assertThat(status).isEqualTo(Status.FAILED);
    }

    @Test
    void workspaceWritesReachTheHost() throws Exception {
        Path workspace = Files.createDirectory(tempDir.resolve("workspace"));

        Status status = execute(workspace, stage("write", "echo built > /work/out.txt", false));

        assertThat(status).isEqualTo(Status.SUCCEEDED);
        assertThat(Files.readString(workspace.resolve("out.txt"))).isEqualTo("built\n");
    }

    @Test
    void networkIsClosedUnlessTheStageAsksForIt() throws Exception {
        Path workspace = Files.createDirectory(tempDir.resolve("workspace"));
        String fetch = "wget -q -T 5 -O /dev/null http://example.com";

        assertThat(execute(workspace, stage("closed", fetch, false))).isEqualTo(Status.FAILED);
    }

    @Test
    void containersDoNotCarryStateBetweenStages() throws Exception {
        Path workspace = Files.createDirectory(tempDir.resolve("workspace"));

        assertThat(execute(workspace, stage("first", "touch /tmp/marker", false)))
                .isEqualTo(Status.SUCCEEDED);
        assertThat(execute(workspace, stage("second", "test -f /tmp/marker", false)))
                .isEqualTo(Status.FAILED);
    }

    private static Status execute(Path workspace, StageSpec stage) {
        ExecProperties props = new ExecProperties(4, false, "1", "1g", "/usr/local/bin:/usr/bin:/bin", "/tmp");
        CommandRunner runner = new CommandRunner(ToolProperties.defaults());
        StageExecutor executor = new StageExecutor(
                runner, new ContainerRunner(runner, props, ToolProperties.defaults()), props, new SecretsProperties(Map.of()));
        PushEvent event = new PushEvent(
                Provider.GITHUB, "octo/hello", "https://github.com/octo/hello.git", "main", "abc1234", "octocat", 1);
        return executor.execute(stage, new RunContext("itest01", workspace, event));
    }

    private static StageSpec stage(String name, String command, boolean network) {
        return new StageSpec(
                "isolation",
                name,
                name,
                List.of(),
                Condition.SUCCESS,
                List.of(command),
                List.of(),
                Map.of(),
                List.of(),
                null,
                Duration.ofMinutes(2),
                0,
                false,
                IMAGE,
                network,
                List.of(),
                null);
    }

    private static int run(String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();
        if (!process.waitFor(2, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            return -1;
        }
        return process.exitValue();
    }
}
