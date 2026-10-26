package dev.rodeo.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rodeo.config.RodeoProperties;
import dev.rodeo.config.StagesProperties;
import dev.rodeo.config.SecretsProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PipelineParserTest {

    private final PipelineParser parser = new PipelineParser(new RodeoProperties(4, true, new StagesProperties(null, "1", "1g")), new SecretsProperties(Map.of("DEPLOY_TOKEN", "test-token")));

    @Test
    void parsesPipelinesAndDefaultsToSequentialStages() {
        PipelineFile file = parser.parse("""
                on = ["main"]

                [frontend]
                build = "npm ci && npm run build"
                test = { run = "npm test" }
                deploy = { run = ["./deploy.sh"], needs = ["test"] }
                """);

        List<StageSpec> stages = file.stagesFor("main");
        assertThat(stages).extracting(StageSpec::id)
                .containsExactly("frontend/build", "frontend/test", "frontend/deploy");
        assertThat(stages.get(0).needs()).isEmpty();
        assertThat(stages.get(1).needs()).containsExactly("frontend/build");
        assertThat(stages.get(2).needs()).containsExactly("frontend/test");
        assertThat(stages.get(0).run()).containsExactly("npm ci && npm run build");
    }

    @Test
    void labelDefaultsToStageNameAndCanBeSet() {
        PipelineFile file = parser.parse("""
                on = ["main"]

                [frontend]
                build = "npm run build"
                deploy = { run = "./deploy.sh", label = "Deploy frontend to CDN" }
                """);

        List<StageSpec> stages = file.stagesFor("main");
        assertThat(stages.get(0).label()).isEqualTo("build");
        assertThat(stages.get(1).label()).isEqualTo("Deploy frontend to CDN");
    }

    @Test
    void rejectsUnknownStageOptionInsteadOfIgnoringIt() {
        assertThatThrownBy(() -> parser.parse("""
                on = ["main"]

                [app]
                deploy = { run = "./deploy.sh", timout = "5m" }
                """))
                .isInstanceOf(PipelineException.class)
                .hasMessageContaining("unknown option 'timout'");
    }

    @Test
    void rejectsBlankLabel() {
        assertThatThrownBy(() -> parser.parse("""
                on = ["main"]

                [app]
                build = { run = "make", label = "" }
                """))
                .isInstanceOf(PipelineException.class)
                .hasMessageContaining("label");
    }

    @Test
    void emptyNeedsStartsAStageImmediately() {
        PipelineFile file = parser.parse("""
                on = ["main"]

                [site]
                build = "make"
                lint = { run = "make lint", needs = [] }
                """);

        StageSpec lint = file.stagesFor("main").get(1);
        assertThat(lint.needs()).isEmpty();
    }

    @Test
    void crossPipelineNeedsUseDotNotation() {
        PipelineFile file = parser.parse("""
                on = ["main"]

                [backend]
                build = "mvn -B package"
                deploy = { run = "./deploy.sh", needs = ["build"] }

                [frontend]
                build = "npm run build"
                deploy = { run = "./deploy.sh", needs = ["build", "backend.deploy"] }
                """);

        StageSpec frontendDeploy = file.stagesFor("main").stream()
                .filter(stage -> stage.id().equals("frontend/deploy"))
                .findFirst()
                .orElseThrow();
        assertThat(frontendDeploy.needs()).containsExactly("frontend/build", "backend/deploy");
    }

    @Test
    void stagesForBranchOnlyIncludesTriggeredPipelines() {
        PipelineFile file = parser.parse("""
                [web]
                on = ["main"]
                build = "make"

                [docs]
                on = ["gh-pages"]
                publish = "make publish"
                """);

        assertThat(file.stagesFor("main")).extracting(StageSpec::id).containsExactly("web/build");
        assertThat(file.stagesFor("gh-pages")).extracting(StageSpec::id).containsExactly("docs/publish");
        assertThat(file.stagesFor("feature")).isEmpty();
    }

    @Test
    void pipelineEnvIsInheritedByEveryStage() {
        PipelineFile file = parser.parse("""
                on = ["main"]

                [api]
                env = { JAVA_OPTS = "-Xmx512m" }
                build = "mvn package"
                deploy = { run = "./deploy.sh", env = { REGION = "eu" } }
                """);

        StageSpec deploy = file.stagesFor("main").get(1);
        assertThat(deploy.env()).containsEntry("JAVA_OPTS", "-Xmx512m").containsEntry("REGION", "eu");
    }

    @Test
    void legacyCommandsFormatStillWorks() {
        PipelineFile file = parser.parse("""
                branches = ["main"]
                commands = ["mvn -B test", "./deploy.sh"]
                """);

        List<StageSpec> stages = file.stagesFor("main");
        assertThat(stages).hasSize(1);
        assertThat(stages.get(0).run()).containsExactly("mvn -B test", "./deploy.sh");
    }

    @Test
    void readsOptionsWithDefaults() {
        PipelineFile file = parser.parse("""
                on = ["main"]

                [app]
                build = { run = "make", timeout = "2h", retries = 2, optional = true, on_fail = "./notify.sh" }
                """);

        StageSpec build = file.stagesFor("main").get(0);
        assertThat(build.timeout()).isEqualTo(Duration.ofHours(2));
        assertThat(build.retries()).isEqualTo(2);
        assertThat(build.allowFailure()).isTrue();
        assertThat(build.onFailure()).containsExactly("./notify.sh");
    }

    @Test
    void rejectsDependencyCycle() {
        assertThatThrownBy(() -> parser.parse("""
                on = ["main"]

                [app]
                a = { run = "true", needs = ["b"] }
                b = { run = "true", needs = ["a"] }
                """))
                .isInstanceOf(PipelineException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void rejectsUnknownNeed() {
        assertThatThrownBy(() -> parser.parse("""
                on = ["main"]

                [app]
                build = { run = "true", needs = ["missing"] }
                """))
                .isInstanceOf(PipelineException.class)
                .hasMessageContaining("unknown stage app/missing");
    }

    @Test
    void rejectsSecretTheServerDoesNotAllow() {
        assertThatThrownBy(() -> parser.parse("""
                on = ["main"]

                [app]
                deploy = { run = "./deploy.sh", secrets = ["ROOT_PASSWORD"] }
                """))
                .isInstanceOf(PipelineException.class)
                .hasMessageContaining("ROOT_PASSWORD");
    }

    @Test
    void rejectsDirectoryOutsideRepository() {
        assertThatThrownBy(() -> parser.parse("""
                on = ["main"]

                [app]
                build = { run = "make", dir = "../elsewhere" }
                """))
                .isInstanceOf(PipelineException.class)
                .hasMessageContaining("inside the repository");
    }

    @Test
    void rejectsUnknownTopLevelScalar() {
        assertThatThrownBy(() -> parser.parse("""
                on = ["main"]
                color = "blue"
                """))
                .isInstanceOf(PipelineException.class)
                .hasMessageContaining("unknown top-level key 'color'");
    }

    @Test
    void parsesTeardownAndEnvFile() {
        PipelineFile file = parser.parse("""
                on = ["main"]

                [app.deploy]
                run = "./deploy.sh"
                teardown = ["docker compose down", "rm -f state"]
                env_file = "deploy/production.env"
                """);

        StageSpec deploy = file.stagesFor("main").getFirst();
        assertThat(deploy.teardown()).containsExactly("docker compose down", "rm -f state");
        assertThat(deploy.envFile()).isEqualTo("deploy/production.env");
    }

    @Test
    void rejectsEnvFileOutsideTheRepository() {
        assertThatThrownBy(() -> parser.parse("""
                on = ["main"]

                [app]
                deploy = { run = "./deploy.sh", env_file = "/etc/rodeo/secrets.env" }
                """))
                .isInstanceOf(PipelineException.class)
                .hasMessageContaining("inside the repository");
    }
}
