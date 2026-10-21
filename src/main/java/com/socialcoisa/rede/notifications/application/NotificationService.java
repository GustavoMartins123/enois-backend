package com.socialcoisa.rede.notifications.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.socialcoisa.rede.notifications.application.port.out.NotificationRepository;
import com.socialcoisa.rede.notifications.domain.Notification;
import com.socialcoisa.rede.shared.web.ApiException;

@Service
public class NotificationService {

	private final NotificationRepository repository;
	private final ApplicationEventPublisher events;
	private final Clock clock;
	private final com.socialcoisa.rede.shared.blocks.BlockedUsersReader blocks;
	private static final java.util.Set<String> ESSENTIAL_KINDS = java.util.Set.of(
		"SANCAO", "RECURSO", "MODERACAO", "SEGURANCA", "SISTEMA", "SUPORTE");

	public NotificationService(NotificationRepository repository, ApplicationEventPublisher events, Clock clock,
			com.socialcoisa.rede.shared.blocks.BlockedUsersReader blocks) {
		this.repository = repository;
		this.events = events;
		this.clock = clock;
		this.blocks = blocks;
	}

	/** Returns null when a blocked social contact is suppressed, including its push event. */
	@Transactional
	public Notification create(String userId, String kind, String actorId, String title, String body, String href,
			String ctaLabel) {
		if (!ESSENTIAL_KINDS.contains(kind) && blocks.hiddenIdsFor(userId).contains(actorId)) {
			return null;
		}
		String id = "notification_" + UUID.randomUUID().toString().replace("-", "");
		Notification notification = Notification.create(id, userId, kind, actorId, title, body, href, ctaLabel,
			clock.instant());
		repository.save(notification);
		// Quem entrega fora do app (push no celular) escuta este evento depois do commit.
		events.publishEvent(new NotificationCreatedEvent(userId, kind, title, body, href));
		return notification;
	}

	@Transactional
	public Notification markRead(String notificationId, String userId) {
		Notification notification = repository.findById(notificationId)
			.orElseThrow(() -> new ApiException(404, "NOTIFICATION_NOT_FOUND", "Notificacao nao encontrada."));
		if (!notification.userId().equals(userId)) {
			throw new ApiException(403, "FORBIDDEN", "Esta notificacao nao pertence a voce.");
		}
		repository.markRead(notificationId);
		return notification.markRead();
	}

	@Transactional
	public void markAllRead(String userId) {
		repository.markAllReadByUser(userId);
	}

	public List<Notification> list(String userId, java.time.Instant before, int limit) {
		return repository.findByUserId(userId, before, limit);
	}

}
