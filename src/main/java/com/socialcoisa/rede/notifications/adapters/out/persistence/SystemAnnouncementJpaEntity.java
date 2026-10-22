package com.socialcoisa.rede.notifications.adapters.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "system_announcements")
class SystemAnnouncementJpaEntity {

	@Id
	@Column(name = "announcement_key")
	private String announcementKey;

	@Column(name = "sent_at", nullable = false)
	private Instant sentAt;

	protected SystemAnnouncementJpaEntity() {
	}

	SystemAnnouncementJpaEntity(String announcementKey, Instant sentAt) {
		this.announcementKey = announcementKey;
		this.sentAt = sentAt;
	}

	String getAnnouncementKey() {
		return announcementKey;
	}

	Instant getSentAt() {
		return sentAt;
	}
}
