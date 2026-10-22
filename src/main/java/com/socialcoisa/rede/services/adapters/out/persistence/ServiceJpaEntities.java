package com.socialcoisa.rede.services.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.services.domain.ServiceModels.ServiceContract;
import com.socialcoisa.rede.services.domain.ServiceModels.ServiceListing;
import com.socialcoisa.rede.services.domain.ServiceModels.ServiceProposal;
import com.socialcoisa.rede.services.domain.ServiceModels.ServiceRequest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "service_listings")
class ServiceListingJpaEntity {

	@Id
	private String id;

	@Column(name = "seller_id", nullable = false)
	private String sellerId;

	@Column(nullable = false, length = 40)
	private String category;

	@Column(nullable = false, length = 160)
	private String title;

	@Column(nullable = false, length = 2000)
	private String description;

	@Column(name = "deliverables", nullable = false, length = 1000)
	private String deliverables;

	@Column(nullable = false)
	private long price;

	@Column(nullable = false, length = 8)
	private String currency = "FAISCA";

	@Column(name = "delivery_days", nullable = false)
	private int deliveryDays;

	@Column(nullable = false, length = 16)
	private String status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	static ServiceListingJpaEntity from(ServiceListing l) {
		ServiceListingJpaEntity e = new ServiceListingJpaEntity();
		e.id = l.id();
		e.sellerId = l.sellerId();
		e.category = l.category();
		e.title = l.title();
		e.description = l.description();
		e.deliverables = l.deliverables();
		e.price = l.price();
		e.currency = l.currency() == null ? "FAISCA" : l.currency();
		e.deliveryDays = l.deliveryDays();
		e.status = l.status().name();
		e.createdAt = l.createdAt();
		e.updatedAt = l.updatedAt();
		return e;
	}

	ServiceListing toDomain() {
		return new ServiceListing(id, sellerId, category, title, description, deliverables, price, currency,
			deliveryDays, ServiceListing.Status.valueOf(status), createdAt, updatedAt);
	}
}

@Entity
@Table(name = "service_requests")
class ServiceRequestJpaEntity {

	@Id
	private String id;

	@Column(name = "requester_id", nullable = false)
	private String requesterId;

	@Column(nullable = false, length = 40)
	private String category;

	@Column(nullable = false, length = 160)
	private String title;

	@Column(nullable = false, length = 2000)
	private String description;

	@Column(name = "deliverables", nullable = false, length = 1000)
	private String deliverables;

	@Column(name = "budget_min", nullable = false)
	private long budgetMin;

	@Column(name = "budget_max", nullable = false)
	private long budgetMax;

	@Column(name = "deadline")
	private Instant deadline;

	@Column(nullable = false, length = 16)
	private String status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	static ServiceRequestJpaEntity from(ServiceRequest r) {
		ServiceRequestJpaEntity e = new ServiceRequestJpaEntity();
		e.id = r.id();
		e.requesterId = r.requesterId();
		e.category = r.category();
		e.title = r.title();
		e.description = r.description();
		e.deliverables = r.deliverables();
		e.budgetMin = r.budgetMin();
		e.budgetMax = r.budgetMax();
		e.deadline = r.deadline();
		e.status = r.status().name();
		e.createdAt = r.createdAt();
		e.updatedAt = r.updatedAt();
		return e;
	}

	ServiceRequest toDomain() {
		return new ServiceRequest(id, requesterId, category, title, description, deliverables, budgetMin, budgetMax,
			deadline, ServiceRequest.Status.valueOf(status), createdAt, updatedAt);
	}
}

@Entity
@Table(name = "service_proposals")
class ServiceProposalJpaEntity {

	@Id
	private String id;

	@Column(name = "request_id", nullable = false)
	private String requestId;

	@Column(name = "user_id", nullable = false)
	private String userId;

	@Column(nullable = false, length = 1000)
	private String message;

	@Column(nullable = false)
	private long price;

	@Column(nullable = false, length = 16)
	private String status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	static ServiceProposalJpaEntity from(ServiceProposal p) {
		ServiceProposalJpaEntity e = new ServiceProposalJpaEntity();
		e.id = p.id();
		e.requestId = p.requestId();
		e.userId = p.userId();
		e.message = p.message();
		e.price = p.price();
		e.status = p.status().name();
		e.createdAt = p.createdAt();
		return e;
	}

	ServiceProposal toDomain() {
		return new ServiceProposal(id, requestId, userId, message, price, ServiceProposal.Status.valueOf(status),
			createdAt);
	}
}

@Entity
@Table(name = "service_contracts")
class ServiceContractJpaEntity {

	@Id
	private String id;

	@Column(name = "listing_id")
	private String listingId;

	@Column(name = "request_id")
	private String requestId;

	@Column(name = "buyer_id", nullable = false)
	private String buyerId;

	@Column(name = "seller_id", nullable = false)
	private String sellerId;

	@Column(nullable = false)
	private long price;

	@Column(name = "escrow_state", nullable = false, length = 16)
	private String escrowState;

	@Column(name = "deliverable_note", length = 1000)
	private String deliverableNote;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "accepted_at")
	private Instant acceptedAt;

	static ServiceContractJpaEntity from(ServiceContract c) {
		ServiceContractJpaEntity e = new ServiceContractJpaEntity();
		e.id = c.id();
		e.listingId = c.listingId();
		e.requestId = c.requestId();
		e.buyerId = c.buyerId();
		e.sellerId = c.sellerId();
		e.price = c.price();
		e.escrowState = c.escrowState().name();
		e.deliverableNote = c.deliverableNote();
		e.createdAt = c.createdAt();
		e.acceptedAt = c.acceptedAt();
		return e;
	}

	ServiceContract toDomain() {
		return new ServiceContract(id, listingId, requestId, buyerId, sellerId, price,
			ServiceContract.EscrowState.valueOf(escrowState), deliverableNote, createdAt, acceptedAt);
	}
}
