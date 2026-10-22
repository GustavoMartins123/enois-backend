package com.socialcoisa.rede.stats.application.port.out;

import java.time.Instant;

public interface CommunityStatsReader {

	/** Usuarios com atividade a partir de {@code since} (sessao viva ou conteudo publicado). */
	long countActiveSince(Instant since);

	/** Total de contas na plataforma. */
	long countUsers();
}
