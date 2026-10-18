package com.socialcoisa.rede.games.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.socialcoisa.rede.games.application.port.out.GameFavoriteRepository;
import com.socialcoisa.rede.games.application.port.out.GameRepository;
import com.socialcoisa.rede.games.domain.Game;
import com.socialcoisa.rede.games.domain.GameFavorite;
import com.socialcoisa.rede.games.domain.GameStatus;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * A biblioteca de quem joga: o que a pessoa guardou.
 *
 * Guardar é idempotente (guardar duas vezes não cria duas linhas — o índice único garante) e
 * desguardar também. Só jogo publicado entra: guardar um rascunho de outra pessoa não faz sentido.
 */
@Service
public class GameLibraryService {

	private static final int MAX_LIBRARY = 200;

	private final GameFavoriteRepository favorites;
	private final GameRepository games;
	private final Clock clock;

	public GameLibraryService(GameFavoriteRepository favorites, GameRepository games, Clock clock) {
		this.favorites = favorites;
		this.games = games;
		this.clock = clock;
	}

	@Transactional
	public boolean toggle(String userId, String gameId) {
		Game game = games.findById(gameId)
			.orElseThrow(() -> new ApiException(404, "GAME_NOT_FOUND", "Jogo não encontrado."));
		if (game.status() != GameStatus.PUBLISHED) {
			throw new ApiException(409, "GAME_NOT_PUBLISHED", "Este jogo não está publicado.");
		}
		if (favorites.exists(userId, gameId)) {
			favorites.delete(userId, gameId);
			return false;
		}
		favorites.save(GameFavorite.create("gfav_" + UUID.randomUUID().toString().replace("-", ""), userId, gameId,
			clock.instant()));
		return true;
	}

	@Transactional(readOnly = true)
	public boolean has(String userId, String gameId) {
		return userId != null && favorites.exists(userId, gameId);
	}

	/** A biblioteca: os favoritos, com o jogo de cada um (o que saiu do ar sai da lista). */
	@Transactional(readOnly = true)
	public List<Game> library(String userId) {
		List<Game> items = new ArrayList<>();
		for (GameFavorite favorite : favorites.findByUser(userId, MAX_LIBRARY)) {
			games.findById(favorite.gameId())
				.filter(game -> game.status() == GameStatus.PUBLISHED)
				.ifPresent(items::add);
		}
		return items;
	}
}
