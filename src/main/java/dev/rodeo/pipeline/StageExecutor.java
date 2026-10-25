package dev.rodeo.pipeline;

import dev.rodeo.model.PushEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class StageExecutor {

    private static final Logger log = LoggerFactory.getLogger(StageExecutor.class);
    private static final Duration RETRY_DELAY = Duration.ofSeconds(10);

    private final CommandRunner commandRunner;

    public StageExecutor(CommandRunner commandRunner) {
        this.commandRunner = commandRunner;
    }

    public Status execute(StageSpec stage, RunContext context) {
        String tag = "[" + context.runId() + " " + stage.id() + "]";
        String output = tag + " | ";
        Instant start = Instant.now();
        Path directory = stage.dir() == null
                ? context.workspace()
                : context.workspace().resolve(stage.dir()).normalize();
        try {
            Map<String, String> environment = environment(stage, context);
            log.info("{} STARTED  {}", tag, stage.label());

            String failure = null;
            for (int attempt = 0; attempt <= stage.retries(); attempt++) {
                if (attempt > 0) {
                    log.warn("{} RETRY {} of {} in {}", tag, attempt, stage.retries(), Elapsed.format(RETRY_DELAY));
                    Thread.sleep(RETRY_DELAY.toMillis());
                }
                failure = runAll(stage.run(), directory, environment, tag, output, stage.timeout());
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
                runAll(stage.onFailure(), directory, environment, tag, output, stage.timeout());
            }
            log.error("{} FAILED in {}: {}", tag, Elapsed.format(elapsed(start)), failure);
            return Status.FAILED;
        } catch (IllegalStateException e) {
            log.error("{} FAILED: {}", tag, e.getMessage());
            return Status.FAILED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("{} FAILED: interrupted", tag);
            return Status.FAILED;
        }
    }

    private String runAll(
            List<String> commands,
            Path directory,
            Map<String, String> environment,
            String tag,
            String output,
            Duration timeout) throws InterruptedException {
        for (int step = 1; step <= commands.size(); step++) {
            String command = commands.get(step - 1);
            String position = "step " + step + "/" + commands.size();
            log.info("{} {} $ {}", tag, position, command);
            try {
                int exitCode = commandRunner.execute(command, directory, environment, output, timeout);
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

    private static Map<String, String> environment(StageSpec stage, RunContext context) {
        Map<String, String> environment = new HashMap<>();
        for (String name : List.of("PATH", "HOME")) {
            String value = System.getenv(name);
            if (value != null) {
                environment.put(name, value);
            }
        }
        environment.putAll(stage.env());
        for (String secret : stage.secrets()) {
            String value = System.getenv(secret);
            if (value == null) {
                throw new IllegalStateException("secret " + secret + " is not set on this server");
            }
            environment.put(secret, value);
        }

        PushEvent event = context.event();
        environment.put("RODEO_PIPELINE", stage.pipeline());
        environment.put("RODEO_STAGE", stage.name());
        environment.put("RODEO_REPOSITORY", event.repository());
        environment.put("RODEO_BRANCH", event.branch());
        environment.put("RODEO_COMMIT", event.after());
        return environment;
    }
}
