package com.socialcoisa.rede.moderation.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.moderation.domain.SanctionStatus;
import com.socialcoisa.rede.moderation.domain.SanctionType;
import com.socialcoisa.rede.moderation.domain.UserSanction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_sanctions", indexes = @Index(name = "idx_user_sanctions_user", columnList = "user_id, status"))
class UserSanctionJpaEntity {

	@Id
	private String id;

	@Column(name = "user_id", nullable = false)
	private String userId;

	@Column(name = "report_id")
	private String reportId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SanctionType type;

	@Column(length = 2000)
	private String reason;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SanctionStatus status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "removed_at")
	private Instant removedAt;

	@Column(name = "removed_by")
	private String removedBy;

	protected UserSanctionJpaEntity() {
	}

	static UserSanctionJpaEntity fromDomain(UserSanction sanction) {
		UserSanctionJpaEntity e = new UserSanctionJpaEntity();
		e.id = sanction.id();
		e.userId = sanction.userId();
		e.reportId = sanction.reportId();
		e.type = sanction.type();
		e.reason = sanction.reason();
		e.status = sanction.status();
		e.createdAt = sanction.createdAt();
		e.removedAt = sanction.removedAt();
		e.removedBy = sanction.removedBy();
		return e;
	}

	UserSanction toDomain() {
		return UserSanction.restore(id, userId, reportId, type, reason, status, createdAt, removedAt, removedBy);
	}
}
