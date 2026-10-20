package com.socialcoisa.rede.services.adapters.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.socialcoisa.rede.services.domain.ServiceCategory;
import com.socialcoisa.rede.services.domain.ServiceOffer;
import com.socialcoisa.rede.services.domain.ServiceStatus;

@Entity
@Table(name = "service_offers")
class ServiceOfferJpaEntity {

	@Id
	@Column(name = "id", nullable = false)
	private String id;

	@Column(name = "user_id", nullable = false)
	private String userId;

	@Column(name = "slug", nullable = false)
	private String slug;

	@Column(name = "title", nullable = false)
	private String title;

	@Column(name = "description", nullable = false, columnDefinition = "varchar(4000)")
	private String description;

	@Column(name = "link", length = 500)
	private String link;

	@Column(name = "category", nullable = false)
	private String category;

	@Column(name = "status", nullable = false)
	private String status;

	@Column(name = "reviewed_by")
	private String reviewedBy;

	@Column(name = "review_note")
	private String reviewNote;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected ServiceOfferJpaEntity() {
	}

	static ServiceOfferJpaEntity fromDomain(ServiceOffer offer) {
		ServiceOfferJpaEntity entity = new ServiceOfferJpaEntity();
		entity.id = offer.id();
		entity.userId = offer.userId();
		entity.slug = offer.slug();
		entity.title = offer.title();
		entity.description = offer.description();
		entity.link = offer.link();
		entity.category = offer.category().name();
		entity.status = offer.status().name();
		entity.reviewedBy = offer.reviewedBy();
		entity.reviewNote = offer.reviewNote();
		entity.createdAt = offer.createdAt();
		entity.updatedAt = offer.updatedAt();
		return entity;
	}

	ServiceOffer toDomain() {
		return new ServiceOffer(id, userId, slug, title, description, link, ServiceCategory.valueOf(category),
			ServiceStatus.valueOf(status), reviewedBy, reviewNote, createdAt, updatedAt);
	}
}
