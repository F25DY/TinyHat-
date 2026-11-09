package com.hexora.hexora.security;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;
import org.springframework.stereotype.Component;

@Component
public class SessionTerminationEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public SessionTerminationEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @EventListener
    public void onSessionDestroyed(HttpSessionDestroyedEvent event) {
        eventPublisher.publishEvent(new SessionTerminatedEvent(event.getSession().getId()));
    }
}
