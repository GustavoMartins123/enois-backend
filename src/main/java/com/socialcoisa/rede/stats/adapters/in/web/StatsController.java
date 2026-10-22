package com.socialcoisa.rede.stats.adapters.in.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.socialcoisa.rede.stats.application.CommunityStats;
import com.socialcoisa.rede.stats.application.StatsService;

/**
 * Numeros publicos da comunidade. Sem sessao de proposito: a landing mostra o contador para quem
 * ainda nao entrou, e o dado e agregado (nao identifica ninguem).
 */
@RestController
class StatsController {

	private final StatsService statsService;

	StatsController(StatsService statsService) {
		this.statsService = statsService;
	}

	@GetMapping("/stats/community")
	CommunityStats community() {
		return statsService.community();
	}
}
