package dev.rodeo.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

public record GitLabPushPayload(
        String ref,
        String after,
        @JsonProperty("user_username") String userUsername,
        Project project,
        List<Commit> commits) {
    public PushEvent toPushEvent() {
        return new PushEvent(
                Provider.GITLAB,
                project.pathWithNamespace(),
                project.gitHttpUrl(),
                PushEvent.branchFromRef(ref),
                after,
                userUsername,
                Objects.requireNonNullElse(commits, List.of()).size());
    }

    public record Project(
            @JsonProperty("path_with_namespace") String pathWithNamespace,
            @JsonProperty("git_http_url") String gitHttpUrl) {
    }

    public record Commit(String id) {
    }
}
