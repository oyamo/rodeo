package dev.rodeo.exec;

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

    private static final Logger log = LoggerFactory.getLogger(CommandRunner.class);
    private static final Duration TIMEOUT = Duration.ofMinutes(10);

    public void run(List<String> commands, Path workingDirectory) throws IOException, InterruptedException {
        for (String command : commands) {
            int exitCode = execute(command, workingDirectory);
            if (exitCode != 0) {
                throw new IllegalStateException("Command exited with " + exitCode + ": " + command);
            }
        }
    }

    private int execute(String command, Path workingDirectory) throws IOException, InterruptedException {
        log.info("Running '{}' in {}", command, workingDirectory);
        ProcessBuilder builder = new ProcessBuilder("sh", "-c", command)
                .directory(workingDirectory.toFile())
                .inheritIO();
        Map<String, String> environment = builder.environment();
        environment.clear();
        for (String name : List.of("PATH", "HOME")) {
            String value = System.getenv(name);
            if (value != null) {
                environment.put(name, value);
            }
        }

        Process process = builder.start();
        if (!process.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Command timed out after " + TIMEOUT + ": " + command);
        }
        return process.exitValue();
    }
}
