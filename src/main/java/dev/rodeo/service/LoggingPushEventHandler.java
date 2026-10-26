package dev.rodeo.service;

import dev.rodeo.model.PushEvent;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class LoggingPushEventHandler implements PushEventHandler {
    private static final Logger log = LoggerFactory.getLogger(LoggingPushEventHandler.class);

    @Override
    public void handle(PushEvent event) {
        log.info("{} push to {} on branch {} by {}: {} commit(s), head {}",
                event.provider(),
                event.repository(),
                event.branch(),
                Objects.requireNonNullElse(event.pusher(), "unknown"),
                event.commitCount(),
                event.after());
    }
}
