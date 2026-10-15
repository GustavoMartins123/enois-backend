package com.socialcoisa.rede.identity.adapters.out.persistence;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataOAuthAuthorizationRequestJpaRepository
		extends JpaRepository<OAuthAuthorizationRequestJpaEntity, String> {

	List<OAuthAuthorizationRequestJpaEntity> findByExpiresAtBefore(Instant now);
}