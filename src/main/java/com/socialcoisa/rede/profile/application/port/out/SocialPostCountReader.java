package com.socialcoisa.rede.profile.application.port.out;

/**
 * Quantas publicacoes o perfil SOCIAL do autor tem. Vive no perfil porque o card de estatisticas
 * e do perfil — a implementacao vem do modulo de feed, onde o post mora.
 */
public interface SocialPostCountReader {

	/** Inteiro do que a listagem do perfil mostra (mesmos filtros: ativo, social, sem blog, sem canal). */
	int countByUser(String userId);
}
