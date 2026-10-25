package dev.rodeo.exec;

import dev.rodeo.model.PushEvent;
import dev.rodeo.service.PushEventHandler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

@Component
public class PushCommandsHandler implements PushEventHandler {

    private static final Logger log = LoggerFactory.getLogger(PushCommandsHandler.class);
    private static final String CONFIG_FILE = "rodeo.toml";
    private static final Duration CLONE_TIMEOUT = Duration.ofMinutes(5);

    private final ExecutorService executor;
    private final CommandRunner runner;

    public PushCommandsHandler(ExecutorService pushExecutor, CommandRunner runner) {
        this.executor = pushExecutor;
        this.runner = runner;
    }

    @Override
    public void handle(PushEvent event) {
        if (event.branch().startsWith("refs/")) {
            return;
        }
        executor.submit(() -> runForPush(event));
    }

    private void runForPush(PushEvent event) {
        try {
            Path workspace = Files.createTempDirectory("rodeo-");
            try {
                runInWorkspace(event, workspace);
            } finally {
                FileSystemUtils.deleteRecursively(workspace);
            }
        } catch (Exception e) {
            log.error("Commands failed for {} on branch {}", event.repository(), event.branch(), e);
        }
    }

    private void runInWorkspace(PushEvent event, Path workspace) throws IOException, InterruptedException {
        clone(event, workspace);

        Path configFile = workspace.resolve(CONFIG_FILE);
        if (!Files.exists(configFile)) {
            log.info("No {} in {}; nothing to run", CONFIG_FILE, event.repository());
            return;
        }

        RepoConfig config = RepoConfig.parse(Files.readString(configFile));
        if (!config.branches().contains(event.branch())) {
            log.info("Branch {} is not listed in {} for {}", event.branch(), CONFIG_FILE, event.repository());
            return;
        }

        runner.run(config.commands(), workspace);
        log.info("Finished commands for {} on branch {}", event.repository(), event.branch());
    }

    private void clone(PushEvent event, Path workspace) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(
                "git", "clone", "--depth", "1", "--branch", event.branch(),
                "--", event.cloneUrl(), workspace.toString())
                .inheritIO();
        Map<String, String> environment = builder.environment();
        environment.put("GIT_TERMINAL_PROMPT", "0");

        Process process = builder.start();
        if (!process.waitFor(CLONE_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("git clone timed out for " + event.repository());
        }
        if (process.exitValue() != 0) {
            throw new IOException("git clone failed for " + event.repository() + " with exit " + process.exitValue());
        }
    }
}
