package dev.rodeo.pipeline;

import dev.rodeo.config.Platform;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CommandRunner {

    public static final int TIMED_OUT = -1;

    private static final Logger log = LoggerFactory.getLogger(CommandRunner.class);
    private static final Duration OUTPUT_GRACE = Duration.ofSeconds(5);
    private static final Runnable NOTHING = () -> { };

    public record Invocation(
            List<String> argv,
            Path directory,
            Map<String, String> environment,
            String prefix,
            Duration timeout) {
    }

    public int execute(String command, Path directory, Map<String, String> environment, String prefix, Duration timeout)
            throws IOException, InterruptedException {
        List<String> argv = List.of(Platform.shell(), Platform.shellFlag(), command);
        return run(new Invocation(argv, directory, environment, prefix, timeout), NOTHING);
    }

    /** Runs the command and returns its exit code, or {@link #TIMED_OUT} after killing it. */
    public int run(Invocation invocation, Runnable onTimeout) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(invocation.argv())
                .directory(invocation.directory().toFile())
                .redirectErrorStream(true);
        builder.environment().clear();
        builder.environment().putAll(invocation.environment());

        Process process = builder.start();
        Thread output = Thread.ofVirtual().start(() -> relay(process, invocation.prefix()));
        if (process.waitFor(invocation.timeout().toMillis(), TimeUnit.MILLISECONDS)) {
            output.join();
            return process.exitValue();
        }
        stopTree(process);
        onTimeout.run();
        output.join(OUTPUT_GRACE.toMillis());
        log.error("{}timed out after {}: {}", invocation.prefix(), invocation.timeout(),
                String.join(" ", invocation.argv()));
        return TIMED_OUT;
    }

    private static void stopTree(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    private static void relay(Process process, String prefix) {
        try (BufferedReader reader = process.inputReader()) {
            reader.lines().forEach(line -> log.info("{}| {}", prefix, line));
        } catch (IOException e) {
            log.warn("{}output closed: {}", prefix, e.getMessage());
        }
    }
}
