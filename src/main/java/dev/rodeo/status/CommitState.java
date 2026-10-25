package dev.rodeo.status;

import java.util.Locale;

public enum CommitState {
    PENDING,
    SUCCESS,
    FAILURE;

    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
