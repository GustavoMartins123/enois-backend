package com.socialcoisa.rede.moderation.application.port.out;

import java.time.Instant;
import java.util.List;

/**
 * Leitura de contas recentes com os sinais objetivos que entram no score de suspeita.
 * É só leitura: a decisão (banir em lote) continua sendo humana e passa pelo fluxo de sanções.
 */
public interface SuspiciousUserReader {

	record Candidate(String id, String username, String name, String email, String avatar, String bio,
			Instant createdAt, Instant emailVerifiedAt, long postCount, long commentCount, long reportCount,
			boolean moderator) {
	}

	List<Candidate> findRecent(Instant since, int limit);

	/**
	 * Contas cujo username OU nome contém um dos padrões (ex.: "probe"), dentro da janela.
	 * Caminho independente do score: usado pela contenção direta do padrão de ataque.
	 */
	List<Candidate> findMatching(List<String> patterns, Instant since, int limit);
}
