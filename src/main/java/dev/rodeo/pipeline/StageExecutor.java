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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public StageExecutor(CommandRunner commandRunner, ContainerRunner containerRunner, SecretsProperties secrets) {
        this.commandRunner = commandRunner;
        this.containerRunner = containerRunner;
        this.secrets = secrets;
    }

    public Status execute(StageSpec stage, RunContext context) {
        String tag = "[" + context.runId() + " " + stage.id() + "]";
        Instant start = Instant.now();
        try {
            Map<String, String> environment = environment(stage, context, stage.image() != null, secrets);
            Launcher launcher = launcherFor(stage, context, environment, tag + OUTPUT_SEPARATOR);
            return runAttempts(stage, launcher, tag, start);
        } catch (IllegalStateException e) {
            log.error("{} FAILED: {}", tag, e.getMessage());
            return Status.FAILED;
        } catch (IOException e) {
            log.error("{} FAILED: could not read {}: {}", tag, ENV_FILE_KEY, e.getMessage());
            return Status.FAILED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("{} FAILED: interrupted", tag);
            return Status.FAILED;
        }
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

    private static Path directoryFor(StageSpec stage, RunContext context) {
        if (stage.dir() == null) {
            return context.workspace();
        }
        return context.workspace().resolve(stage.dir()).normalize();
    }

    private Status runAttempts(StageSpec stage, Launcher launcher, String tag, Instant start)
            throws InterruptedException {
        Optional<String> failure = Optional.empty();
        for (int attempt = 0; attempt <= stage.retries(); attempt++) {
            if (attempt > 0) {
                log.warn("{} RETRY {} of {} in {}", tag, attempt, stage.retries(), Elapsed.format(RETRY_DELAY));
                Thread.sleep(RETRY_DELAY.toMillis());
            }
            runTeardown(stage, launcher, tag, attempt);
            failure = runAll(stage.run(), launcher, tag, stage.timeout());
            if (failure.isEmpty()) {
                log.info("{} SUCCEEDED in {}", tag, Elapsed.format(elapsed(start)));
                return Status.SUCCEEDED;
            }
            if (attempt < stage.retries()) {
                log.warn("{} attempt {} failed: {}", tag, attempt + 1, failure.get());
            }
        }

        if (!stage.onFailure().isEmpty()) {
            log.warn("{} running on_fail commands", tag);
            runAll(stage.onFailure(), launcher, tag, stage.timeout());
        }
        log.error("{} FAILED in {}: {}", tag, Elapsed.format(elapsed(start)), failure.orElse(""));
        return Status.FAILED;
    }

    private void runTeardown(StageSpec stage, Launcher launcher, String tag, int attempt)
            throws InterruptedException {
        if (stage.teardown().isEmpty()) {
            return;
        }
        log.info("{} teardown before attempt {}", tag, attempt + 1);
        if (runAll(stage.teardown(), launcher, tag, stage.timeout()).isPresent()) {
            log.warn("{} teardown did not complete cleanly; continuing", tag);
        }
    }

    private static Optional<String> runAll(List<String> commands, Launcher launcher, String tag, Duration timeout)
            throws InterruptedException {
        for (int step = 1; step <= commands.size(); step++) {
            String command = commands.get(step - 1);
            String position = "step " + step + "/" + commands.size();
            log.info("{} {} $ {}", tag, position, command);
            try {
                int exitCode = launcher.launch(command);
                if (exitCode == CommandRunner.TIMED_OUT) {
                    return Optional.of(position + " timed out after " + Elapsed.format(timeout));
                }
                if (exitCode != 0) {
                    return Optional.of(position + " exited with " + exitCode);
                }
            } catch (IOException e) {
                return Optional.of(position + " could not start: " + e.getMessage());
            }
        }
        return Optional.empty();
    }

    private static String containerName(RunContext context, StageSpec stage) {
        String suffix = UUID.randomUUID().toString().substring(0, CONTAINER_SUFFIX_LENGTH);
        return CONTAINER_PREFIX + context.runId() + "-" + stage.id().replace('/', '-') + "-" + suffix;
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

    @FunctionalInterface
    private interface Launcher {

        int launch(String command) throws IOException, InterruptedException;
    }
}
