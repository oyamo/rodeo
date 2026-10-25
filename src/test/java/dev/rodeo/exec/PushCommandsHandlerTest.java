package dev.rodeo.exec;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rodeo.config.ExecProperties;
import dev.rodeo.config.ToolProperties;
import dev.rodeo.config.SecretsProperties;
import dev.rodeo.model.Provider;
import dev.rodeo.model.PushEvent;
import dev.rodeo.pipeline.CommandRunner;
import dev.rodeo.pipeline.ContainerRunner;
import dev.rodeo.pipeline.PipelineParser;
import dev.rodeo.pipeline.StageExecutor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PushCommandsHandlerTest {

    @TempDir
    Path tempDir;

    @Test
    void clonesRepoAndRunsCommandsForListedBranch() throws Exception {
        Path remote = createRemote("""
                branches = ["main"]
                commands = ["echo done > '%s'"]
                """.formatted(tempDir.resolve("marker.txt")));

        runPush(remote, "main");

        assertThat(tempDir.resolve("marker.txt")).exists();
    }

    @Test
    void skipsBranchesNotListedInConfig() throws Exception {
        Path remote = createRemote("""
                branches = ["main"]
                commands = ["echo done > '%s'"]
                """.formatted(tempDir.resolve("marker.txt")));
        git(remote, "branch", "feature");

        runPush(remote, "feature");

        assertThat(tempDir.resolve("marker.txt")).doesNotExist();
    }

    @Test
    void stopsAtFirstFailingCommand() throws Exception {
        Path remote = createRemote("""
                branches = ["main"]
                commands = ["false", "echo done > '%s'"]
                """.formatted(tempDir.resolve("marker.txt")));

        runPush(remote, "main");

        assertThat(tempDir.resolve("marker.txt")).doesNotExist();
    }

    @Test
    void deploysFrontendAndBackendPipelinesWithCrossDependency() throws Exception {
        Path backend = tempDir.resolve("backend.txt");
        Path frontend = tempDir.resolve("frontend.txt");
        Path frontendBuild = tempDir.resolve("frontend-build.txt");
        Path remote = createRemote("""
                on = ["main"]

                [backend]
                deploy = "echo backend > '%s'"

                [frontend]
                build = "echo built > '%s'"
                deploy = { run = "test -f '%s' && test -f '%s' && echo frontend > '%s'", needs = ["build", "backend.deploy"] }
                """.formatted(backend, frontendBuild, frontendBuild, backend, frontend));

        runPush(remote, "main");

        assertThat(frontend).exists();
        assertThat(Files.readString(frontend)).isEqualTo("frontend\n");
    }

    @Test
    void reportsPendingThenSuccessForEachPipeline() throws Exception {
        Path remote = createRemote("""
                on = ["main"]

                [app]
                build = "echo ok"
                """);

        List<String> statuses = runPush(remote, "main");

        assertThat(statuses).hasSize(2);
        assertThat(statuses.get(0)).startsWith("rodeo/app PENDING Run ").endsWith(" started");
        assertThat(statuses.get(1)).isEqualTo("rodeo/app SUCCESS All stages succeeded");
    }

    @Test
    void reportsFailureNamingTheFailedStage() throws Exception {
        Path remote = createRemote("""
                on = ["main"]

                [app]
                build = "echo ok"
                deploy = { run = "false", label = "Deploy API" }
                """);

        List<String> statuses = runPush(remote, "main");

        assertThat(statuses).hasSize(2);
        assertThat(statuses.get(0)).startsWith("rodeo/app PENDING Run ").endsWith(" started");
        assertThat(statuses.get(1)).isEqualTo("rodeo/app FAILURE Failed: Deploy API");
    }

    private List<String> runPush(Path remote, String branch) throws InterruptedException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        ExecutorService stagePool = Executors.newFixedThreadPool(4);
        ExecProperties props = new ExecProperties(4, true, "1", "1g", "/usr/local/bin:/usr/bin:/bin", "/tmp");
        SecretsProperties secrets = new SecretsProperties(Map.of("DEPLOY_TOKEN", "test-token"));
        CommandRunner commandRunner = new CommandRunner(ToolProperties.defaults());
        PipelineParser parser = new PipelineParser(props, secrets);
        List<String> statuses = new CopyOnWriteArrayList<>();
        PushCommandsHandler handler = new PushCommandsHandler(
                executor,
                stagePool,
                parser,
                new StageExecutor(commandRunner, new ContainerRunner(commandRunner, props, ToolProperties.defaults()), props, secrets),
                (event, context, state, description) ->
                        statuses.add(context + " " + state + " " + description), props, ToolProperties.defaults());

        handler.handle(new PushEvent(
                Provider.GITHUB,
                "octo/hello",
                remote.toUri().toString(),
                branch,
                "abc",
                "octocat",
                1));
        executor.shutdown();
        executor.awaitTermination(60, TimeUnit.SECONDS);
        stagePool.shutdown();
        return statuses;
    }

    private Path createRemote(String rodeoToml) throws IOException, InterruptedException {
        Path remote = Files.createTempDirectory(tempDir, "remote-");
        Files.writeString(remote.resolve("rodeo.toml"), rodeoToml);
        git(remote, "init", "-b", "main");
        git(remote, "add", "rodeo.toml");
        git(remote, "-c", "user.name=test", "-c", "user.email=test@example.com", "commit", "-m", "init");
        return remote;
    }

    private static void git(Path directory, String... args) throws IOException, InterruptedException {
        String[] command = new String[args.length + 1];
        command[0] = "git";
        System.arraycopy(args, 0, command, 1, args.length);
        Process process = new ProcessBuilder(command).directory(directory.toFile()).inheritIO().start();
        if (process.waitFor() != 0) {
            throw new IOException("git " + String.join(" ", args) + " failed");
        }
    }
}
