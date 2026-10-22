package com.socialcoisa.rede.projects.adapters.out.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataUserProjectJpaRepository extends JpaRepository<UserProjectJpaEntity, String> {

	List<UserProjectJpaEntity> findByUserIdOrderByPositionAscCreatedAtAsc(String userId);
}
