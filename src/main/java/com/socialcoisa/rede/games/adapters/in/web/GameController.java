package com.socialcoisa.rede.games.adapters.in.web;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.socialcoisa.rede.games.application.GameService;
import com.socialcoisa.rede.games.domain.Game;
import com.socialcoisa.rede.shared.web.AdminGuard;
import com.socialcoisa.rede.shared.web.CurrentUser;
import com.socialcoisa.rede.shared.web.CurrentUserResolver;

/**
 * Agregador de jogos: o criador envia o LINK, a moderação revisa e o jogo roda EMBUTIDO
 * (sandbox + tela cheia). O Énóis não hospeda.
 */
@RestController
class GameController {

	private final GameService games;
	private final CurrentUserResolver currentUserResolver;
	private final AdminGuard adminGuard;
	private final com.socialcoisa.rede.profile.application.port.out.UserProfileRepository profiles;

	GameController(GameService games, CurrentUserResolver currentUserResolver, AdminGuard adminGuard,
			com.socialcoisa.rede.profile.application.port.out.UserProfileRepository profiles) {
		this.games = games;
		this.currentUserResolver = currentUserResolver;
		this.adminGuard = adminGuard;
		this.profiles = profiles;
	}

	// --- leitura pública ---

	@GetMapping("/games")
	List<GameView> catalog(@RequestParam(defaultValue = "60") int limit) {
		return withUsernames(games.catalog(limit));
	}

	@GetMapping("/games/{username}")
	List<GameView> byUser(@PathVariable String username) {
		return withUsernames(games.publishedByUser(username));
	}

	@GetMapping("/games/{username}/{gameSlug}")
	GameView bySlug(@PathVariable String username, @PathVariable String gameSlug) {
		return withUsernames(List.of(games.byUserAndSlug(username, gameSlug))).get(0);
	}

	/** O endereço público do jogo é /games/{username}/{slug} — o username sai do perfil. */
	private List<GameView> withUsernames(List<Game> list) {
		Map<String, String> names = new java.util.HashMap<>();
		profiles.findAllByIds(list.stream().map(Game::userId).distinct().toList())
			.forEach(p -> names.put(p.id(), p.username()));
		return list.stream()
			.map(g -> new GameView(g.id(), g.userId(), names.get(g.userId()), g.slug(), g.title(), g.description(),
				g.cover(), g.url(), g.tags(), g.status().name(), g.reviewNote(), g.submittedAt().toString()))
			.toList();
	}

	// --- criador ---

	@GetMapping("/me/games")
	List<GameView> myGames(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		return withUsernames(games.ownedBy(user.id().value()));
	}

	@PostMapping("/games")
	Map<String, Object> submit(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestBody SubmitGameRequest request) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		Game created = games.submit(user.id().value(), request.slug(), request.title(), request.description(),
			request.cover(), request.url(), request.tags());
		return Map.of("id", created.id(), "slug", created.slug(), "status", created.status().name());
	}

	@PostMapping("/games/{gameId}/resubmit")
	Map<String, Object> resubmit(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String gameId) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		Game game = games.resubmit(gameId, user.id().value());
		return Map.of("id", game.id(), "status", game.status().name());
	}

	// --- moderação ---

	@GetMapping("/admin/games")
	List<GameView> pending(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestParam(defaultValue = "30") int limit) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		adminGuard.requireAdmin(user);
		return withUsernames(games.pending(limit));
	}

	@PostMapping("/admin/games/{gameId}/approve")
	Map<String, Object> approve(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String gameId, @RequestBody(required = false) ReviewRequest request) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		adminGuard.requireAdmin(user);
		Game approved = games.approve(gameId, user.id().value(), request == null ? null : request.note());
		return Map.of("id", approved.id(), "status", approved.status().name());
	}

	@PostMapping("/admin/games/{gameId}/reject")
	Map<String, Object> reject(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String gameId, @RequestBody(required = false) ReviewRequest request) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		adminGuard.requireAdmin(user);
		Game rejected = games.reject(gameId, user.id().value(), request == null ? null : request.note());
		return Map.of("id", rejected.id(), "status", rejected.status().name());
	}

	record SubmitGameRequest(String slug, String title, String description, String cover, String url, String tags) {
	}

	record ReviewRequest(String note) {
	}

	record GameView(String id, String userId, String username, String slug, String title, String description,
			String cover, String url, String tags, String status, String reviewNote, String submittedAt) {
	}
}
