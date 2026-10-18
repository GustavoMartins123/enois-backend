package com.socialcoisa.rede.games.adapters.out.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataDeveloperFollowJpaRepository extends JpaRepository<DeveloperFollowJpaEntity, String> {

	boolean existsByFollowerIdAndDeveloperUserId(String followerId, String developerUserId);

	@Modifying
	@Query("DELETE FROM DeveloperFollowJpaEntity f WHERE f.followerId = :followerId AND f.developerUserId = :developer")
	int deleteByPair(@Param("followerId") String followerId, @Param("developer") String developerUserId);

	long countByDeveloperUserId(String developerUserId);

	List<DeveloperFollowJpaEntity> findByFollowerId(String followerId);

	List<DeveloperFollowJpaEntity> findByDeveloperUserId(String developerUserId);
}
