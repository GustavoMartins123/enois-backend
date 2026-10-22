package com.socialcoisa.rede.games.adapters.out.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataDeveloperProfileJpaRepository extends JpaRepository<DeveloperProfileJpaEntity, String> {

	List<DeveloperProfileJpaEntity> findByUserIdIn(Collection<String> userIds);
}
