package com.socialcoisa.rede.securityaudit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.socialcoisa.rede.notifications.application.NotificationCreatedEvent;
import com.socialcoisa.rede.notifications.application.NotificationService;
import com.socialcoisa.rede.notifications.application.port.out.NotificationRepository;

class NotificationContactAuditTests {
    @Test
    void aBlockedContactProducesNeitherRowNorPushEvent() {
        var rows = mock(NotificationRepository.class);
        var events = mock(ApplicationEventPublisher.class);
        var service = new NotificationService(rows, events, Clock.systemUTC(), user -> Set.of("blocked-actor"));
        assertThat(service.create("recipient", "NEW_REPLY", "blocked-actor", "Reply", "text", "/feed", "Ver"))
            .isNull();
        verifyNoInteractions(rows, events);
    }

    @Test
    void aNormalContactStillCreatesANotificationAndPushEvent() {
        var rows = mock(NotificationRepository.class);
        var events = mock(ApplicationEventPublisher.class);
        var service = new NotificationService(rows, events, Clock.systemUTC(), user -> Set.of());
        var notification = service.create("recipient", "NEW_REPLY", "actor", "Reply", "text", "/feed", "Ver");
        assertThat(notification).isNotNull();
        verify(rows).save(notification);
        verify(events).publishEvent(any(NotificationCreatedEvent.class));
    }

    @Test
    void blockingAStaffMemberDoesNotSuppressTheSanctionOrAppealDecision() {
        var rows = mock(NotificationRepository.class);
        var events = mock(ApplicationEventPublisher.class);
        var service = new NotificationService(rows, events, Clock.systemUTC(), user -> Set.of("reviewer"));
        assertThat(service.create("recipient", "RECURSO", "reviewer", "Decisao", "text", "/penalidades", "Ver"))
            .isNotNull();
        verify(rows).save(any());
        verify(events).publishEvent(any(NotificationCreatedEvent.class));
    }
}
