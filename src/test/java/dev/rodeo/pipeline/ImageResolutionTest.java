package dev.rodeo.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rodeo.config.ExecProperties;
import dev.rodeo.config.SecretsProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ImageResolutionTest {

    private final PipelineParser withHost = new PipelineParser(new ExecProperties(4, true, "1", "1g", "/usr/local/bin:/usr/bin:/bin", "/tmp"), new SecretsProperties(Map.of("DEPLOY_TOKEN", "test-token")));
    private final PipelineParser withoutHost = new PipelineParser(new ExecProperties(4, false, "1", "1g", "/usr/local/bin:/usr/bin:/bin", "/tmp"), new SecretsProperties(Map.of("DEPLOY_TOKEN", "test-token")));

    @Test
    void topLevelImageIsInheritedByEveryPipeline() {
        PipelineFile file = withoutHost.parse("""
                on = ["main"]
                image = "maven:3.9"

                [api]
                build = "mvn package"
                """);

        assertThat(file.stagesFor("main").getFirst().image()).isEqualTo("maven:3.9");
    }

    @Test
    void pipelineImageOverridesTheTopLevelImage() {
        PipelineFile file = withoutHost.parse("""
                on = ["main"]
                image = "maven:3.9"

                [web]
                image = "node:22"
                build = "npm run build"
                """);

        assertThat(file.stagesFor("main").getFirst().image()).isEqualTo("node:22");
    }

    @Test
    void stageImageOverridesThePipelineImage() {
        PipelineFile file = withoutHost.parse("""
                on = ["main"]

                [web]
                image = "node:22"
                build = { run = "npm run build", image = "node:20" }
                """);

        assertThat(file.stagesFor("main").getFirst().image()).isEqualTo("node:20");
    }

    @Test
    void stageWithoutImageIsRejectedWhenHostExecutionIsDisabled() {
        assertThatThrownBy(() -> withoutHost.parse("""
                on = ["main"]

                [app]
                deploy = "./deploy.sh"
                """))
                .isInstanceOf(PipelineException.class)
                .hasMessageContaining("app/deploy has no image")
                .hasMessageContaining("host execution is disabled");
    }

    @Test
    void stageWithoutImageRunsOnHostWhenTheServerAllowsIt() {
        PipelineFile file = withHost.parse("""
                on = ["main"]

                [app]
                deploy = "./deploy.sh"
                """);

        assertThat(file.stagesFor("main").getFirst().image()).isNull();
    }

    @Test
    void hostStageRunsOnTheServerEvenWhenThePipelineHasAnImage() {
        PipelineFile file = withHost.parse("""
                on = ["main"]
                image = "alpine:3.20"

                [app]
                build = "make"
                deploy = { run = "systemctl restart app", host = true }
                """);

        assertThat(file.stagesFor("main").get(0).image()).isEqualTo("alpine:3.20");
        assertThat(file.stagesFor("main").get(1).image()).isNull();
    }

    @Test
    void hostStageIsRejectedWhenHostExecutionIsDisabled() {
        assertThatThrownBy(() -> withoutHost.parse("""
                on = ["main"]
                image = "alpine:3.20"

                [app]
                deploy = { run = "systemctl restart app", host = true }
                """))
                .isInstanceOf(PipelineException.class)
                .hasMessageContaining("host execution is disabled");
    }

    @Test
    void hostAndImageCannotBothBeSet() {
        assertThatThrownBy(() -> withHost.parse("""
                on = ["main"]

                [app]
                deploy = { run = "true", host = true, image = "alpine:3.20" }
                """))
                .isInstanceOf(PipelineException.class)
                .hasMessageContaining("sets both host and image");
    }

    @Test
    void rejectsAnInvalidImageReference() {
        assertThatThrownBy(() -> withoutHost.parse("""
                on = ["main"]

                [app]
                build = { run = "make", image = "bad image; rm -rf /" }
                """))
                .isInstanceOf(PipelineException.class)
                .hasMessageContaining("container image reference");
    }

    @Test
    void networkNeedsAnImage() {
        assertThatThrownBy(() -> withHost.parse("""
                on = ["main"]

                [app]
                fetch = { run = "curl https://example.com", network = true }
                """))
                .isInstanceOf(PipelineException.class)
                .hasMessageContaining("sets network, which needs an image");
    }

    @Test
    void networkIsOffByDefault() {
        PipelineFile file = withoutHost.parse("""
                on = ["main"]
                image = "alpine:3.20"

                [app]
                build = "make"
                """);

        assertThat(file.stagesFor("main").getFirst().network()).isFalse();
    }
}
