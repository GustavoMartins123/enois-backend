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

import com.socialcoisa.rede.games.application.GameCatalogService;
import com.socialcoisa.rede.games.application.GamePageView;
import com.socialcoisa.rede.games.application.GameSearchCriteria;
import com.socialcoisa.rede.games.application.GameService;
import com.socialcoisa.rede.games.application.GameView;
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
	private final GameCatalogService catalog;
	private final CurrentUserResolver currentUserResolver;
	private final AdminGuard adminGuard;
	private final com.socialcoisa.rede.profile.application.port.out.UserProfileRepository profiles;

	GameController(GameService games, GameCatalogService catalog, CurrentUserResolver currentUserResolver,
			AdminGuard adminGuard,
			com.socialcoisa.rede.profile.application.port.out.UserProfileRepository profiles) {
		this.games = games;
		this.catalog = catalog;
		this.currentUserResolver = currentUserResolver;
		this.adminGuard = adminGuard;
		this.profiles = profiles;
	}

	// --- leitura pública ---

	/**
	 * Catálogo público com busca e filtros. `order` é lista fechada (`recent`, `plays`, `title`) e o
	 * cursor é o deslocamento da próxima página — a resposta diz quando acabou.
	 */
	@GetMapping("/games")
	GamePageView catalog(@RequestParam(required = false) String query, @RequestParam(required = false) String genre,
			@RequestParam(required = false) String tag, @RequestParam(required = false) String stage,
			@RequestParam(required = false) String distribution, @RequestParam(required = false) String order,
			@RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor) {
		var criteria = GameSearchCriteria.of(query, genre, tag, GameSearchCriteria.stageOrNull(stage),
			GameSearchCriteria.distributionOrNull(distribution), order);
		var page = catalog.search(criteria, limit, cursor);
		return new GamePageView(page.items().stream().map(game -> view(game, null)).toList(), page.nextCursor());
	}

	@GetMapping("/games/{username}")
	List<GameView> byUser(@PathVariable String username) {
		return withUsernames(games.publishedByUser(username));
	}

	@GetMapping("/games/{username}/{gameSlug}")
	GameView bySlug(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String username, @PathVariable String gameSlug) {
		// Com sessão, o dono abre o próprio jogo em qualquer estado (rascunho, recusado, suspenso).
		return view(games.byUserAndSlug(username, gameSlug, viewerIdOrNull(userId, sessionToken)), null);
	}

	/** A view de um jogo, com o @ do criador resolvido (o endereço público usa ele). */
	private GameView view(Game game, String viewerId) {
		String username = profiles.findById(game.userId()).map(p -> p.username()).orElse(null);
		return GameView.of(game, username, 0, false, viewerId);
	}

	private String viewerIdOrNull(String userId, String sessionToken) {
		try {
			return currentUserResolver.resolve(userId, sessionToken).id().value();
		}
		catch (Exception ignored) {
			return null;
		}
	}

	/** O endereço público do jogo é /games/{username}/{slug} — o username sai do perfil. */
	private List<GameView> withUsernames(List<Game> list) {
		Map<String, String> names = new java.util.HashMap<>();
		profiles.findAllByIds(list.stream().map(Game::userId).distinct().toList())
			.forEach(p -> names.put(p.id(), p.username()));
		return list.stream()
			.map(g -> GameView.of(g, names.get(g.userId()), 0, false, null))
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

	/** Destaque pago (G2): o criador impulsiona o próprio jogo por 7 dias (5 faíscas). */
	@PostMapping("/games/{gameId}/boost")
	Map<String, Object> boost(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String gameId) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		var boosted = games.boost(gameId, user.id().value());
		return Map.of("id", boosted.id(), "boosted", boosted.boosted(java.time.Instant.now()));
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

}
