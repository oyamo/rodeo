package dev.rodeo.exec;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rodeo.model.Provider;
import dev.rodeo.model.PushEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private void runPush(Path remote, String branch) throws InterruptedException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        PushCommandsHandler handler = new PushCommandsHandler(executor, new CommandRunner());
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
