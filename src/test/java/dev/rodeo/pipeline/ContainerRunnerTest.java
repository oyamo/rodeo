package dev.rodeo.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ContainerRunnerTest {

    private static final List<String> ARGS = ContainerRunner.runArguments(
            "docker",
            "rodeo-run1-app-build-abc123",
            "alpine:3.20",
            false,
            "1",
            "1g",
            Path.of("/tmp/rodeo-workspace"),
            "services/api",
            List.of("DEPLOY_TOKEN", "RODEO_COMMIT"),
            Optional.of("1000:1000"));

    @Test
    void isolatesTheContainerByDefault() {
        assertThat(pairValue("--network")).isEqualTo("none");
        assertThat(pairValue("--cap-drop")).isEqualTo("ALL");
        assertThat(pairValue("--security-opt")).isEqualTo("no-new-privileges");
        assertThat(ARGS).contains("--init", "--rm");
    }

    @Test
    void overridesTheImageEntrypointWithShell() {
        assertThat(pairValue("--entrypoint")).isEqualTo("sh");
    }

    @Test
    void enforcesResourceLimits() {
        assertThat(pairValue("--cpus")).isEqualTo("1");
        assertThat(pairValue("--memory")).isEqualTo("1g");
        assertThat(pairValue("--pids-limit")).isEqualTo(ContainerRunner.PIDS_LIMIT);
    }

    @Test
    void mountsOnlyTheWorkspace() {
        assertThat(pairValue("--mount"))
                .isEqualTo("type=bind,source=/tmp/rodeo-workspace,target=/work");
        assertThat(ARGS.stream().filter("--mount"::equals).count()).isEqualTo(1);
    }

    @Test
    void runsInTheStageDirectoryAsTheWorkspaceOwner() {
        assertThat(pairValue("--workdir")).isEqualTo("/work/services/api");
        assertThat(pairValue("--user")).isEqualTo("1000:1000");
    }

    @Test
    void passesSecretsByNameNotByValue() {
        assertThat(ARGS).containsSubsequence("-e", "DEPLOY_TOKEN", "-e", "RODEO_COMMIT");
        assertThat(ARGS).noneMatch(argument -> argument.contains("=") && argument.startsWith("DEPLOY_TOKEN"));
    }

    @Test
    void endsWithTheImageReference() {
        assertThat(ARGS.getLast()).isEqualTo("alpine:3.20");
    }

    @Test
    void allowsNetworkOnlyWhenTheStageAsksForIt() {
        List<String> networked = ContainerRunner.runArguments(
                "docker", "name", "alpine:3.20", true, "1", "1g", Path.of("/tmp/w"), null, List.of(), Optional.empty());

        assertThat(valueAfter(networked, "--network")).isEqualTo("bridge");
        assertThat(networked).doesNotContain("--user");
    }

    private static String pairValue(String flag) {
        return valueAfter(ARGS, flag);
    }

    private static String valueAfter(List<String> args, String flag) {
        int index = args.indexOf(flag);
        assertThat(index).as("flag %s present", flag).isNotNegative();
        return args.get(index + 1);
    }
}
