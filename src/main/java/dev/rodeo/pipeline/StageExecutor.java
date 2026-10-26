package dev.rodeo.pipeline;

import dev.rodeo.config.Platform;
import dev.rodeo.config.SecretsProperties;
import dev.rodeo.model.PushEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class StageExecutor {

    private static final Logger log = LoggerFactory.getLogger(StageExecutor.class);
    private static final Duration RETRY_DELAY = Duration.ofSeconds(10);
    private static final String CONTAINER_PREFIX = "rodeo-";
    private static final int CONTAINER_SUFFIX_LENGTH = 6;
    private static final String OUTPUT_SEPARATOR = " | ";
    private static final String ENV_FILE_COMMENT = "#";
    private static final String ENV_FILE_KEY = "env_file";
    private static final String ENV_PIPELINE = "RODEO_PIPELINE";
    private static final String ENV_STAGE = "RODEO_STAGE";
    private static final String ENV_REPOSITORY = "RODEO_REPOSITORY";
    private static final String ENV_BRANCH = "RODEO_BRANCH";
    private static final String ENV_COMMIT = "RODEO_COMMIT";

    private final CommandRunner commandRunner;
    private final ContainerRunner containerRunner;
    private final SecretsProperties secrets;
    private final Executor executor;

    public StageExecutor(
            CommandRunner commandRunner,
            ContainerRunner containerRunner,
            SecretsProperties secrets,
            @Qualifier("stagePool") ExecutorService executor) {
        this.commandRunner = commandRunner;
        this.containerRunner = containerRunner;
        this.secrets = secrets;
        this.executor = executor;
    }

    public CompletableFuture<Status> execute(StageSpec stage, RunContext context) {
        String tag = "[" + context.runId() + " " + stage.id() + "]";
        Instant start = Instant.now();
        Map<String, String> environment;
        try {
            environment = environment(stage, context, stage.image() != null, secrets);
        } catch (IllegalStateException e) {
            log.error("{} FAILED: {}", tag, e.getMessage());
            return CompletableFuture.completedFuture(Status.FAILED);
        } catch (IOException e) {
            log.error("{} FAILED: could not read {}: {}", tag, ENV_FILE_KEY, e.getMessage());
            return CompletableFuture.completedFuture(Status.FAILED);
        }
        Launcher launcher = launcherFor(stage, context, environment, tag + OUTPUT_SEPARATOR);
        return attempt(new Run(stage, launcher, tag, start), 0);
    }

    private Launcher launcherFor(StageSpec stage, RunContext context, Map<String, String> environment, String output) {
        if (stage.image() != null) {
            String containerName = containerName(context, stage);
            return command -> containerRunner.execute(
                    stage, command, context.workspace(), containerName, environment, output, stage.timeout());
        }
        Path directory = directoryFor(stage, context);
        return command -> commandRunner.execute(command, directory, environment, output, stage.timeout());
    }

    private CompletableFuture<Status> attempt(Run run, int index) {
        return CompletableFuture.supplyAsync(() -> runOnce(run, index), scheduleFor(run, index))
                .thenCompose(failure -> conclude(run, index, failure));
    }

    private Executor scheduleFor(Run run, int index) {
        if (index == 0) {
            return executor;
        }
        log.warn("{} RETRY {} of {} in {}", run.tag(), index, run.stage().retries(), Elapsed.format(RETRY_DELAY));
        return CompletableFuture.delayedExecutor(RETRY_DELAY.toMillis(), TimeUnit.MILLISECONDS, executor);
    }

    private static Optional<String> runOnce(Run run, int index) {
        if (!run.stage().teardown().isEmpty()) {
            log.info("{} teardown before attempt {}", run.tag(), index + 1);
            if (runAll(run.stage().teardown(), run.launcher(), run.tag(), run.stage().timeout()).isPresent()) {
                log.warn("{} teardown did not complete cleanly; continuing", run.tag());
            }
        }
        return runAll(run.stage().run(), run.launcher(), run.tag(), run.stage().timeout());
    }

    private CompletableFuture<Status> conclude(Run run, int index, Optional<String> failure) {
        if (failure.isEmpty()) {
            log.info("{} SUCCEEDED in {}", run.tag(), Elapsed.format(elapsed(run.start())));
            return CompletableFuture.completedFuture(Status.SUCCEEDED);
        }
        if (index < run.stage().retries()) {
            log.warn("{} attempt {} failed: {}", run.tag(), index + 1, failure.get());
            return attempt(run, index + 1);
        }
        return fail(run, failure.get());
    }

    private CompletableFuture<Status> fail(Run run, String reason) {
        CompletableFuture<Void> cleanup = CompletableFuture.completedFuture(null);
        if (!run.stage().onFailure().isEmpty()) {
            cleanup = CompletableFuture.runAsync(() -> runFailureCommands(run), executor);
        }
        return cleanup.thenApply(ignored -> {
            log.error("{} FAILED in {}: {}", run.tag(), Elapsed.format(elapsed(run.start())), reason);
            return Status.FAILED;
        });
    }

    private static void runFailureCommands(Run run) {
        log.warn("{} running on_fail commands", run.tag());
        runAll(run.stage().onFailure(), run.launcher(), run.tag(), run.stage().timeout());
    }

    private static Optional<String> runAll(List<String> commands, Launcher launcher, String tag, Duration timeout) {
        for (int step = 1; step <= commands.size(); step++) {
            String command = commands.get(step - 1);
            String position = "step " + step + "/" + commands.size();
            log.info("{} {} $ {}", tag, position, command);
            Optional<String> failure = launchStep(launcher, command, position, timeout);
            if (failure.isPresent()) {
                return failure;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> launchStep(Launcher launcher, String command, String position, Duration timeout) {
        try {
            int exitCode = launcher.launch(command);
            if (exitCode == CommandRunner.TIMED_OUT) {
                return Optional.of(position + " timed out after " + Elapsed.format(timeout));
            }
            if (exitCode != 0) {
                return Optional.of(position + " exited with " + exitCode);
            }
            return Optional.empty();
        } catch (IOException e) {
            return Optional.of(position + " could not start: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.of(position + " was interrupted");
        }
    }

    private static String containerName(RunContext context, StageSpec stage) {
        String suffix = UUID.randomUUID().toString().substring(0, CONTAINER_SUFFIX_LENGTH);
        return CONTAINER_PREFIX + context.runId() + "-" + stage.id().replace('/', '-') + "-" + suffix;
    }

    private static Path directoryFor(StageSpec stage, RunContext context) {
        if (stage.dir() == null) {
            return context.workspace();
        }
        return context.workspace().resolve(stage.dir()).normalize();
    }

    private static Duration elapsed(Instant start) {
        return Duration.between(start, Instant.now());
    }

    private static Map<String, String> environment(
            StageSpec stage,
            RunContext context,
            boolean container,
            SecretsProperties secrets) throws IOException {
        Map<String, String> environment = new HashMap<>();
        if (!container) {
            environment.putAll(Platform.environment());
        }
        if (stage.envFile() != null) {
            environment.putAll(loadEnvFile(context.workspace().resolve(stage.envFile())));
        }
        environment.putAll(stage.env());
        for (String name : stage.secrets()) {
            environment.put(name, secrets.values().get(name));
        }

        PushEvent event = context.event();
        environment.put(ENV_PIPELINE, stage.pipeline());
        environment.put(ENV_STAGE, stage.name());
        environment.put(ENV_REPOSITORY, event.repository());
        environment.put(ENV_BRANCH, event.branch());
        environment.put(ENV_COMMIT, event.after());
        return environment;
    }

    static Map<String, String> loadEnvFile(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException(ENV_FILE_KEY + " " + file.getFileName() + " does not exist in the repository");
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : Files.readAllLines(file)) {
            String entry = line.strip();
            int equals = entry.indexOf('=');
            if (!entry.isEmpty() && !entry.startsWith(ENV_FILE_COMMENT) && equals > 0) {
                values.put(entry.substring(0, equals).strip(), entry.substring(equals + 1).strip());
            }
        }
        return values;
    }

    private record Run(StageSpec stage, Launcher launcher, String tag, Instant start) {
    }

    @FunctionalInterface
    private interface Launcher {

        int launch(String command) throws IOException, InterruptedException;
    }
}
