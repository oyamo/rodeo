package dev.rodeo.status;

import dev.rodeo.model.PushEvent;

@FunctionalInterface
public interface StatusReporter {

    void report(PushEvent event, String context, CommitState state, String description);
}
