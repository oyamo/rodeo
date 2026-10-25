package dev.rodeo.service;

import dev.rodeo.model.PushEvent;

public interface PushEventHandler {
    void handle(PushEvent event);
}
