package com.socialcoisa.rede.profile.adapters.out.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataRecommendationJpaRepository extends JpaRepository<RecommendationJpaEntity, String> {

	List<RecommendationJpaEntity> findByRecommendedIdOrderByCreatedAtDesc(String recommendedId);

	List<RecommendationJpaEntity> findByRecommenderIdOrderByCreatedAtDesc(String recommenderId);

	boolean existsByRecommenderIdAndRecommendedId(String recommenderId, String recommendedId);

	/** Recebidas que têm a volta (par recíproco) — não contam para a credibilidade. */
	@Query(value = "select count(*) from user_recommendations r where r.recommended_id = :uid and exists ("
		+ "select 1 from user_recommendations x where x.recommender_id = :uid and x.recommended_id = r.recommender_id)",
		nativeQuery = true)
	long countReciprocal(@Param("uid") String userId);
}
