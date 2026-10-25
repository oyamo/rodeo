package dev.rodeo.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record GitHubPushPayload(
        String ref,
        String after,
        Repository repository,
        Pusher pusher,
        List<Commit> commits) {
    public PushEvent toPushEvent() {
        return new PushEvent(
                Provider.GITHUB,
                repository.fullName(),
                repository.cloneUrl(),
                PushEvent.branchFromRef(ref),
                after,
                pusher == null ? null : pusher.name(),
                commits == null ? 0 : commits.size());
    }

    public record Repository(
            @JsonProperty("full_name") String fullName,
            @JsonProperty("clone_url") String cloneUrl) {
    }

    public record Pusher(String name) {
    }

    public record Commit(String id) {
    }
}
