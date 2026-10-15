package com.socialcoisa.rede.identity.adapters.out.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataIdentityVerificationJpaRepository extends JpaRepository<IdentityVerificationJpaEntity, String> {

	Optional<IdentityVerificationJpaEntity> findByUserId(String userId);
}
