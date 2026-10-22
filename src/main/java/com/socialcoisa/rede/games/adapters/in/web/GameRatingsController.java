package com.socialcoisa.rede.games.adapters.in.web;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.socialcoisa.rede.games.application.GameSocialService;

/** Resumo de nota/comentários em lote para os cards do catálogo (uma requisição por página). */
@RestController
class GameRatingsController {

	private final GameSocialService social;

	GameRatingsController(GameSocialService social) {
		this.social = social;
	}

	@GetMapping("/games/ratings")
	Map<String, GameSocialService.RatingSummary> ratings(@RequestParam List<String> ids) {
		List<String> limited = ids.stream().filter(id -> id != null && !id.isBlank()).distinct().limit(50).toList();
		return social.ratings(limited);
	}
}
