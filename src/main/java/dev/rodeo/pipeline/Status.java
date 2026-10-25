package dev.rodeo.pipeline;

public enum Status {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    SKIPPED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == SKIPPED;
    }
}
