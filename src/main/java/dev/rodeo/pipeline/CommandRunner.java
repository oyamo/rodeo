package dev.rodeo.pipeline;

import dev.rodeo.config.ToolProperties;
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

    private final ToolProperties tools;

    public CommandRunner(ToolProperties tools) {
        this.tools = tools;
    }

    public int execute(String command, Path directory, Map<String, String> environment, String prefix, Duration timeout)
            throws IOException, InterruptedException {
        return run(List.of(tools.shell(), "-c", command), directory, environment, prefix, timeout, () -> { });
    }

    public int run(
            List<String> argv,
            Path directory,
            Map<String, String> environment,
            String prefix,
            Duration timeout,
            Runnable onTimeout) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(argv)
                .directory(directory.toFile())
                .redirectErrorStream(true);
        builder.environment().clear();
        builder.environment().putAll(environment);

        Process process = builder.start();
        Thread output = Thread.ofVirtual().start(() -> relay(process, prefix));

        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            onTimeout.run();
            output.join();
            return TIMED_OUT;
        }
        output.join();
        return process.exitValue();
    }

    private static void relay(Process process, String prefix) {
        try (BufferedReader reader = process.inputReader()) {
            reader.lines().forEach(line -> log.info("{}| {}", prefix, line));
        } catch (IOException e) {
            log.warn("{}output closed: {}", prefix, e.getMessage());
        }
    }
}
