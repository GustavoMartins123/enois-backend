package com.socialcoisa.rede.stats.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.socialcoisa.rede.stats.application.port.out.CommunityStatsReader;

/**
 * Numeros da comunidade para a landing.
 *
 * O resultado fica em cache (`communityStats`, definido em CacheConfig): contar usuarios ativos e
 * uma consulta que varre sessoes, posts e comentarios, e a landing recebe visita sem sessao — nao
 * faz sentido pagar esse preco a cada abertura de pagina. A janela de frescor e curta e explicita,
 * entao o numero pode ficar ate ~10 minutos atras do banco; para um contador de comunidade isso e
 * irrelevante e esta documentado (o front mostra "atualizado ha X").
 */
@Service
public class StatsService {

	/** Janela de "usuario ativo". Mudar aqui muda a definicao do numero exibido na landing. */
	static final int ACTIVE_WINDOW_DAYS = 30;

	private final CommunityStatsReader reader;
	private final Clock clock;

	public StatsService(CommunityStatsReader reader, Clock clock) {
		this.reader = reader;
		this.clock = clock;
	}

	@Cacheable("communityStats")
	public CommunityStats community() {
		Instant now = clock.instant();
		Instant since = now.minus(ACTIVE_WINDOW_DAYS, ChronoUnit.DAYS);
		return new CommunityStats(reader.countActiveSince(since), reader.countUsers(), ACTIVE_WINDOW_DAYS, now);
	}
}
