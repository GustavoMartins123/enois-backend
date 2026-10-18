package com.socialcoisa.rede.games.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.socialcoisa.rede.games.application.port.out.GameEventRepository;
import com.socialcoisa.rede.games.application.port.out.GameFavoriteRepository;
import com.socialcoisa.rede.games.application.port.out.GameRepository;
import com.socialcoisa.rede.games.application.port.out.GameUpdateRepository;
import com.socialcoisa.rede.games.domain.Game;
import com.socialcoisa.rede.games.domain.GameEventKind;
import com.socialcoisa.rede.games.domain.GameStatus;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * O catálogo: achar jogo, abrir jogo e contar o que aconteceu.
 *
 * Duas regras que valem a pena saber:
 *
 * - **só publicado aparece**. Rascunho, enviado, recusado e suspenso ficam fora do catálogo e da
 *   página pública — quem é dono vê o próprio em `/me/games`, e a moderação vê na fila.
 * - **quem conta é o servidor**. `plays` sobe quando alguém começa a jogar (o front avisa pelo
 *   endpoint, e o evento fica registrado). Visualização de página NÃO entra na conta: contador de
 *   "views" é o número mais fácil de inflar e o menos útil para decidir o que jogar.
 */
@Service
public class GameCatalogService {

	/** Teto de itens por página: acima disso é varredura, não navegação. */
	public static final int MAX_PAGE = 48;
	private static final int DEFAULT_PAGE = 24;

	private final GameRepository games;
	private final GameUpdateRepository updates;
	private final GameFavoriteRepository favorites;
	private final GameEventRepository events;
	private final Clock clock;

	public GameCatalogService(GameRepository games, GameUpdateRepository updates, GameFavoriteRepository favorites,
			GameEventRepository events, Clock clock) {
		this.games = games;
		this.updates = updates;
		this.favorites = favorites;
		this.events = events;
		this.clock = clock;
	}

	/** Uma página do catálogo + o cursor da próxima (nulo quando acabou). */
	public record Page(List<Game> items, String nextCursor) {
	}

	@Transactional(readOnly = true)
	public Page search(GameSearchCriteria criteria, Integer limit, String cursor) {
		int size = limit == null ? DEFAULT_PAGE : Math.max(1, Math.min(limit, MAX_PAGE));
		int offset = parseCursor(cursor);
		GameSearchPage found = games.search(criteria, size, offset);
		return new Page(found.items(), found.hasMore() ? String.valueOf(offset + size) : null);
	}

	/** A página do jogo: público só vê publicado; o dono vê o seu em qualquer estado. */
	@Transactional(readOnly = true)
	public Game open(String username, String slug, String viewerId) {
		Game game = games.findByUserAndSlug(username, slug)
			.orElseThrow(() -> new ApiException(404, "GAME_NOT_FOUND", "Jogo não encontrado."));
		boolean owner = viewerId != null && game.ownedBy(viewerId);
		if (game.status() != GameStatus.PUBLISHED && !owner) {
			throw new ApiException(404, "GAME_NOT_FOUND", "Jogo não encontrado.");
		}
		return game;
	}

	/**
	 * Alguém começou a jogar: conta a sessão e registra o evento.
	 *
	 * Não recebe "quantos jogaram" do front — o número é do servidor, e cada sessão é uma linha
	 * (jogador distinto é contado à parte, pelo id de quem está logado).
	 */
	@Transactional
	public int startPlay(String gameId, String viewerId) {
		Game game = games.findById(gameId)
			.orElseThrow(() -> new ApiException(404, "GAME_NOT_FOUND", "Jogo não encontrado."));
		if (game.status() != GameStatus.PUBLISHED) {
			throw new ApiException(409, "GAME_NOT_PUBLISHED", "Este jogo não está publicado.");
		}
		events.record("gevt_" + UUID.randomUUID().toString().replace("-", ""), gameId, GameEventKind.PLAY_START,
			viewerId, clock.instant());
		return games.save(game.countPlay()).plays();
	}

	/** Números do jogo para a página: sessões, jogadores distintos estimados, favoritos e novidades. */
	@Transactional(readOnly = true)
	public Map<String, Object> stats(String gameId) {
		Map<String, Object> stats = new LinkedHashMap<>();
		stats.put("plays", games.findById(gameId).map(Game::plays).orElse(0));
		stats.put("players", events.distinctPlayers(gameId));
		stats.put("favorites", favorites.countByGame(gameId));
		stats.put("updates", updates.countByGame(gameId));
		return stats;
	}

	/** O que a pessoa acompanha: jogos publicados pelos criadores seguidos, mais recentes primeiro. */
	@Transactional(readOnly = true)
	public List<Game> fromFollowedDevelopers(List<String> developerIds, int limit) {
		return games.findPublishedByUsers(new ArrayList<>(developerIds), limit);
	}

	private int parseCursor(String cursor) {
		if (cursor == null || cursor.isBlank()) {
			return 0;
		}
		try {
			return Math.max(0, Integer.parseInt(cursor.trim()));
		}
		catch (NumberFormatException e) {
			return 0;
		}
	}
}
