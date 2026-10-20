package com.socialcoisa.rede.profile.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataUserProfileJpaRepository extends JpaRepository<UserProfileJpaEntity, String> {

	Optional<UserProfileJpaEntity> findByUsername(String username);

	Optional<UserProfileJpaEntity> findByEmail(String email);

	boolean existsByUsername(String username);

	boolean existsByEmail(String email);

	List<UserProfileJpaEntity> findByOrderByReputationDesc();

	/**
	 * O ranking público é a vitrine de QUEM ACABOU DE CHEGAR: os perfis mais recentes primeiro
	 * (decisão do dono do produto em 2026-10-20). A ordenação é no banco, e não em memória.
	 */
	List<UserProfileJpaEntity> findByOrderByCreatedAtDesc(org.springframework.data.domain.Pageable pageable);

	List<UserProfileJpaEntity> findTop20ByNameContainingIgnoreCaseOrUsernameContainingIgnoreCaseOrderByReputationDesc(String name, String username);
}
