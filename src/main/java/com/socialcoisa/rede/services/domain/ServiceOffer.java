package com.socialcoisa.rede.services.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Um serviço oferecido por alguém da comunidade — a mesma mecânica dos jogos: envia, a moderação
 * publica ou recusa, o dono edita. O link é opcional (portfolio, página de contratação...).
 */
public record ServiceOffer(String id, String userId, String slug, String title, String description,
		String link, ServiceCategory category, ServiceStatus status, String reviewedBy, String reviewNote,
		Instant createdAt, Instant updatedAt) {

	public static ServiceOffer submit(String id, String userId, String slug, String title, String description,
			String link, ServiceCategory category, Instant now) {
		return new ServiceOffer(id, userId, slug, title, description, link, category, ServiceStatus.SUBMITTED,
			null, null, now, now);
	}

	public static String newId() {
		return "service_" + UUID.randomUUID().toString().replace("-", "");
	}

	public boolean ownedBy(String userId) {
		return this.userId.equals(userId);
	}

	public ServiceOffer withContent(String newTitle, String newDescription, String newLink,
			ServiceCategory newCategory, Instant now) {
		return new ServiceOffer(id, userId, slug, newTitle, newDescription, newLink, newCategory, status,
			reviewedBy, reviewNote, createdAt, now);
	}

	public ServiceOffer approve(String reviewerId, String note, Instant now) {
		return transition(ServiceStatus.PUBLISHED, reviewerId, note, now);
	}

	public ServiceOffer reject(String reviewerId, String note, Instant now) {
		return transition(ServiceStatus.REJECTED, reviewerId, note, now);
	}

	/** A moderação tirando do ar: some do catálogo, o registro continua (suspender não é apagar). */
	public ServiceOffer suspend(String reviewerId, String note, Instant now) {
		return transition(ServiceStatus.SUSPENDED, reviewerId, note, now);
	}

	public ServiceOffer resubmit(Instant now) {
		if (status != ServiceStatus.REJECTED) {
			return this;
		}
		return new ServiceOffer(id, userId, slug, title, description, link, category, ServiceStatus.SUBMITTED,
			reviewedBy, reviewNote, createdAt, now);
	}

	private ServiceOffer transition(ServiceStatus newStatus, String reviewerId, String note, Instant now) {
		return new ServiceOffer(id, userId, slug, title, description, link, category, newStatus, reviewerId,
			note, createdAt, now);
	}
}
