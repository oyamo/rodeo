package dev.rodeo.pipeline;

import dev.rodeo.config.ExecProperties;
import dev.rodeo.config.ToolProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ContainerRunner {

    static final String WORKSPACE_MOUNT = "/work";
    static final String PIDS_LIMIT = "512";

    private static final Logger log = LoggerFactory.getLogger(ContainerRunner.class);
    private static final Duration KILL_TIMEOUT = Duration.ofSeconds(30);

    private final CommandRunner commandRunner;
    private final ExecProperties properties;
    private final ToolProperties tools;

    public ContainerRunner(CommandRunner commandRunner, ExecProperties properties, ToolProperties tools) {
        this.commandRunner = commandRunner;
        this.properties = properties;
        this.tools = tools;
    }

    public int execute(
            StageSpec stage,
            String command,
            Path workspace,
            String containerName,
            Map<String, String> environment,
            String prefix,
            Duration timeout) throws IOException, InterruptedException {
        List<String> argv = new ArrayList<>(runArguments(
                tools.docker(),
                containerName,
                stage.image(),
                stage.network(),
                properties.stageCpus(),
                properties.stageMemory(),
                workspace,
                stage.dir(),
                environment.keySet(),
                ownerOf(workspace)));
        argv.add("sh");
        argv.add("-c");
        argv.add(command);

        Map<String, String> processEnvironment = new HashMap<>();
        processEnvironment.put("PATH", properties.path());
        processEnvironment.put("HOME", properties.home());
        processEnvironment.putAll(environment);
        return commandRunner.run(argv, workspace, processEnvironment, prefix, timeout,
                () -> kill(containerName));
    }

    static List<String> runArguments(
            String docker,
            String containerName,
            String image,
            boolean network,
            String cpus,
            String memory,
            Path workspace,
            String dir,
            Collection<String> environmentNames,
            Optional<String> owner) {
        List<String> argv = new ArrayList<>(List.of(
                docker, "run",
                "--rm",
                "--init",
                "--name", containerName,
                "--network", network ? "bridge" : "none",
                "--cpus", cpus,
                "--memory", memory,
                "--pids-limit", PIDS_LIMIT,
                "--cap-drop", "ALL",
                "--security-opt", "no-new-privileges",
                "--mount", "type=bind,source=" + workspace + ",target=" + WORKSPACE_MOUNT,
                "--workdir", dir == null ? WORKSPACE_MOUNT : WORKSPACE_MOUNT + "/" + dir));
        owner.ifPresent(id -> {
            argv.add("--user");
            argv.add(id);
        });
        for (String name : environmentNames) {
            argv.add("-e");
            argv.add(name);
        }
        argv.add(image);
        return argv;
    }

    static Optional<String> ownerOf(Path workspace) {
        try {
            int uid = (Integer) Files.getAttribute(workspace, "unix:uid");
            int gid = (Integer) Files.getAttribute(workspace, "unix:gid");
            return Optional.of(uid + ":" + gid);
        } catch (IOException | IllegalArgumentException | UnsupportedOperationException e) {
            return Optional.empty();
        }
    }

    private void kill(String containerName) {
        try {
            ProcessBuilder builder = new ProcessBuilder(tools.docker(), "kill", containerName)
                    .redirectErrorStream(true);
            builder.environment().clear();
            builder.environment().put("PATH", properties.path());
            builder.environment().put("HOME", properties.home());
            Process process = builder.start();
            process.getInputStream().readAllBytes();
            if (!process.waitFor(KILL_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (IOException | InterruptedException e) {
            log.warn("could not kill container {}: {}", containerName, e.getMessage());
        }
    }
}
