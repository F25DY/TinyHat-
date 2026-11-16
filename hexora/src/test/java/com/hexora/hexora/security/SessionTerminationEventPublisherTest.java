package com.hexora.hexora.security;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.session.SessionInformation;

class SessionTerminationEventPublisherTest {

    @Test
    void expiresTheSessionAndPublishesItsTermination() {
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        SessionTerminationEventPublisher publisher = new SessionTerminationEventPublisher(eventPublisher);
        SessionInformation sessionInformation = mock(SessionInformation.class);
        when(sessionInformation.getSessionId()).thenReturn("session-id");

        publisher.expireAndPublish(sessionInformation);

        verify(sessionInformation).expireNow();
        verify(eventPublisher).publishEvent(new SessionTerminatedEvent("session-id"));
    }
}
