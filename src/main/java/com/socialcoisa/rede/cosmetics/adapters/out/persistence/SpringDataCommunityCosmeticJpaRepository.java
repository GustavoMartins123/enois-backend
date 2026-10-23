package com.socialcoisa.rede.cosmetics.adapters.out.persistence;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.socialcoisa.rede.cosmetics.domain.CommunityCosmeticStatus;

interface SpringDataCommunityCosmeticJpaRepository extends JpaRepository<CommunityCosmeticJpaEntity, String> {

	List<CommunityCosmeticJpaEntity> findTop50ByCreatorIdOrderByUpdatedAtDesc(String creatorId);

	List<CommunityCosmeticJpaEntity> findByStatusOrderByUpdatedAtDesc(CommunityCosmeticStatus status,
		Pageable pageable);
}
