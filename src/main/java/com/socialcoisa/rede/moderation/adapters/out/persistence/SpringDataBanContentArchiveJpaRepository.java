package com.socialcoisa.rede.moderation.adapters.out.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataBanContentArchiveJpaRepository extends JpaRepository<BanContentArchiveJpaEntity, String> {

	List<BanContentArchiveJpaEntity> findBySanctionId(String sanctionId);
}
