package com.socialcoisa.rede.profile.application.port.out;

import java.util.List;
import java.util.Optional;

import com.socialcoisa.rede.profile.domain.Recommendation;

public interface RecommendationRepository {

	Recommendation save(Recommendation recommendation);

	Optional<Recommendation> findById(String id);

	List<Recommendation> findByRecommendedId(String recommendedId);

	List<Recommendation> findByRecommenderId(String recommenderId);

	boolean existsByRecommenderAndRecommended(String recommenderId, String recommendedId);

	/**
	 * Recomendações recebidas que são RECÍPROCAS (a pessoa também recomendou quem a recomendou).
	 * Elas não contam para a credibilidade: par mútuo é troca, não confiança.
	 */
	int countReciprocal(String recommendedId);

	void delete(String id);
}
