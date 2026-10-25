package dev.rodeo.pipeline;

import dev.rodeo.config.ExecProperties;
import dev.rodeo.config.SecretsProperties;
import dev.rodeo.model.PushEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class StageExecutor {

    private static final Logger log = LoggerFactory.getLogger(StageExecutor.class);
    private static final Duration RETRY_DELAY = Duration.ofSeconds(10);

    private final CommandRunner commandRunner;
    private final ContainerRunner containerRunner;
    private final ExecProperties execProperties;
    private final SecretsProperties secrets;

    public StageExecutor(
            CommandRunner commandRunner,
            ContainerRunner containerRunner,
            ExecProperties execProperties,
            SecretsProperties secrets) {
        this.commandRunner = commandRunner;
        this.containerRunner = containerRunner;
        this.execProperties = execProperties;
        this.secrets = secrets;
    }

    public Status execute(StageSpec stage, RunContext context) {
        String tag = "[" + context.runId() + " " + stage.id() + "]";
        String output = tag + " | ";
        Instant start = Instant.now();
        Path directory = stage.dir() == null
                ? context.workspace()
                : context.workspace().resolve(stage.dir()).normalize();
        boolean container = stage.image() != null;
        try {
            Map<String, String> environment = environment(stage, context, container, execProperties, secrets);
            String containerName = "rodeo-" + context.runId() + "-" + stage.id().replace('/', '-')
                    + "-" + UUID.randomUUID().toString().substring(0, 6);
            Launcher launcher = container
                    ? command -> containerRunner.execute(
                            stage, command, context.workspace(), containerName, environment, output, stage.timeout())
                    : command -> commandRunner.execute(command, directory, environment, output, stage.timeout());

            String failure = null;
            for (int attempt = 0; attempt <= stage.retries(); attempt++) {
                if (attempt > 0) {
                    log.warn("{} RETRY {} of {} in {}", tag, attempt, stage.retries(), Elapsed.format(RETRY_DELAY));
                    Thread.sleep(RETRY_DELAY.toMillis());
                }
                if (!stage.teardown().isEmpty()) {
                    log.info("{} teardown before attempt {}", tag, attempt + 1);
                    if (runAll(stage.teardown(), launcher, tag, stage.timeout()) != null) {
                        log.warn("{} teardown did not complete cleanly; continuing", tag);
                    }
                }
                failure = runAll(stage.run(), launcher, tag, stage.timeout());
                if (failure == null) {
                    log.info("{} SUCCEEDED in {}", tag, Elapsed.format(elapsed(start)));
                    return Status.SUCCEEDED;
                }
                if (attempt < stage.retries()) {
                    log.warn("{} attempt {} failed: {}", tag, attempt + 1, failure);
                }
            }

            if (!stage.onFailure().isEmpty()) {
                log.warn("{} running on_fail commands", tag);
                runAll(stage.onFailure(), launcher, tag, stage.timeout());
            }
            log.error("{} FAILED in {}: {}", tag, Elapsed.format(elapsed(start)), failure);
            return Status.FAILED;
        } catch (IllegalStateException e) {
            log.error("{} FAILED: {}", tag, e.getMessage());
            return Status.FAILED;
        } catch (IOException e) {
            log.error("{} FAILED: could not read env_file: {}", tag, e.getMessage());
            return Status.FAILED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("{} FAILED: interrupted", tag);
            return Status.FAILED;
        }
    }

    private String runAll(List<String> commands, Launcher launcher, String tag, Duration timeout)
            throws InterruptedException {
        for (int step = 1; step <= commands.size(); step++) {
            String command = commands.get(step - 1);
            String position = "step " + step + "/" + commands.size();
            log.info("{} {} $ {}", tag, position, command);
            try {
                int exitCode = launcher.launch(command);
                if (exitCode == CommandRunner.TIMED_OUT) {
                    return position + " timed out after " + Elapsed.format(timeout);
                }
                if (exitCode != 0) {
                    return position + " exited with " + exitCode;
                }
            } catch (IOException e) {
                return position + " could not start: " + e.getMessage();
            }
        }
        return null;
    }

    private static Duration elapsed(Instant start) {
        return Duration.between(start, Instant.now());
    }

    private static Map<String, String> environment(
            StageSpec stage,
            RunContext context,
            boolean container,
            ExecProperties execProperties,
            SecretsProperties secrets) throws IOException {
        Map<String, String> environment = new HashMap<>();
        if (!container) {
            environment.put("PATH", execProperties.path());
            environment.put("HOME", execProperties.home());
        }
        if (stage.envFile() != null) {
            environment.putAll(loadEnvFile(context.workspace().resolve(stage.envFile())));
        }
        environment.putAll(stage.env());
        for (String name : stage.secrets()) {
            environment.put(name, secrets.values().get(name));
        }

        PushEvent event = context.event();
        environment.put("RODEO_PIPELINE", stage.pipeline());
        environment.put("RODEO_STAGE", stage.name());
        environment.put("RODEO_REPOSITORY", event.repository());
        environment.put("RODEO_BRANCH", event.branch());
        environment.put("RODEO_COMMIT", event.after());
        return environment;
    }

    static Map<String, String> loadEnvFile(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException("env_file " + file.getFileName() + " does not exist in the repository");
        }
        Map<String, String> values = new HashMap<>();
        for (String line : Files.readAllLines(file)) {
            String entry = line.strip();
            int equals = entry.indexOf('=');
            if (entry.isEmpty() || entry.startsWith("#") || equals <= 0) {
                continue;
            }
            values.put(entry.substring(0, equals).strip(), entry.substring(equals + 1).strip());
        }
        return values;
    }

    @FunctionalInterface
    private interface Launcher {

        int launch(String command) throws IOException, InterruptedException;
    }
}
