package dev.rodeo.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

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
                commits == null ? 0 : commits.size());
    }

    public record Project(
            @JsonProperty("path_with_namespace") String pathWithNamespace,
            @JsonProperty("git_http_url") String gitHttpUrl) {
    }

    public record Commit(String id) {
    }
}
