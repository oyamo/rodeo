package dev.rodeo.pipeline;

import dev.rodeo.config.Platform;
import dev.rodeo.config.RodeoProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
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
    private static final String DOCKER = "docker";
    private static final String CONTAINER_SHELL = "sh";
    private static final String BRIDGE_NETWORK = "bridge";
    private static final String NO_NETWORK = "none";
    private static final Duration KILL_TIMEOUT = Duration.ofSeconds(30);

    private final CommandRunner commandRunner;
    private final RodeoProperties properties;

    public ContainerRunner(CommandRunner commandRunner, RodeoProperties properties) {
        this.commandRunner = commandRunner;
        this.properties = properties;
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
                Platform.tool(DOCKER),
                containerName,
                stage.image(),
                stage.network(),
                properties.stages().cpus(),
                properties.stages().memory(),
                workspace,
                stage.dir(),
                environment.keySet(),
                ownerOf(workspace)));
        argv.add("-c");
        argv.add(command);

        Map<String, String> processEnvironment = new HashMap<>(Platform.environment());
        processEnvironment.putAll(environment);
        CommandRunner.Invocation invocation =
                new CommandRunner.Invocation(argv, workspace, processEnvironment, prefix, timeout);
        return commandRunner.run(invocation, () -> kill(containerName));
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
                "--network", networkMode(network),
                "--cpus", cpus,
                "--memory", memory,
                "--pids-limit", PIDS_LIMIT,
                "--entrypoint", CONTAINER_SHELL,
                "--cap-drop", "ALL",
                "--security-opt", "no-new-privileges",
                "--mount", "type=bind,source=" + workspace + ",target=" + WORKSPACE_MOUNT,
                "--workdir", workdir(dir)));
        owner.ifPresent(id -> argv.addAll(List.of("--user", id)));
        for (String name : environmentNames) {
            argv.addAll(List.of("-e", name));
        }
        argv.add(image);
        return argv;
    }

    private static String networkMode(boolean network) {
        if (network) {
            return BRIDGE_NETWORK;
        }
        return NO_NETWORK;
    }

    private static String workdir(String dir) {
        if (dir == null) {
            return WORKSPACE_MOUNT;
        }
        return WORKSPACE_MOUNT + "/" + dir;
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
            commandRunner.run(new CommandRunner.Invocation(
                    List.of(Platform.tool(DOCKER), "kill", containerName),
                    Platform.home(),
                    Platform.environment(),
                    "",
                    KILL_TIMEOUT), () -> { });
        } catch (IOException | InterruptedException e) {
            log.warn("could not kill container {}: {}", containerName, e.getMessage());
        }
    }
}
