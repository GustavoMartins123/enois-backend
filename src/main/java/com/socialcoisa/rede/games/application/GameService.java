package com.socialcoisa.rede.games.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.games.application.port.out.GameRepository;
import com.socialcoisa.rede.games.domain.Game;
import com.socialcoisa.rede.games.domain.GameStatus;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Agregador de jogos: o Énóis NÃO hospeda — o criador envia o LINK (o jogo fica publicado
 * em itch.io/site próprio), a moderação revisa no mesmo quadro em que vai rodar, e o jogo
 * publicado roda EMBUTIDO com sandbox e tela cheia. Envio → revisão → publicação.
 */
@Service
public class GameService {

	private final GameRepository games;
	private final com.socialcoisa.rede.profile.application.port.out.UserProfileRepository profiles;
	private final Clock clock;

	public GameService(GameRepository games,
			com.socialcoisa.rede.profile.application.port.out.UserProfileRepository profiles, Clock clock) {
		this.games = games;
		this.profiles = profiles;
		this.clock = clock;
	}

	@Transactional
	public Game submit(String userId, String slugBase, String title, String description, String cover, String url,
			String tags) {
		if (!StringUtils.hasText(title) || title.trim().length() < 3) {
			throw new ApiException(400, "INVALID_TITLE", "Título do jogo precisa de pelo menos 3 caracteres.");
		}
		if (!StringUtils.hasText(url) || !url.trim().toLowerCase(Locale.ROOT).startsWith("https://")) {
			throw new ApiException(400, "INVALID_GAME_URL", "O link do jogo precisa ser https.");
		}
		if (url.trim().length() > 500 || StringUtils.containsWhitespace(url.trim())) {
			throw new ApiException(400, "INVALID_GAME_URL", "Link inválido.");
		}
		String slug = uniqueSlug(userId, StringUtils.hasText(slugBase) ? slugFrom(slugBase) : slugFrom(title));
		Game game = Game.submit("game_" + UUID.randomUUID().toString().replace("-", ""), userId, slug, title.trim(),
			StringUtils.hasText(description) ? description.trim() : null,
			StringUtils.hasText(cover) ? cover.trim() : null, url.trim(),
			StringUtils.hasText(tags) ? tags.trim() : null, clock.instant());
		return games.save(game);
	}

	@Transactional
	public Game resubmit(String gameId, String userId) {
		Game game = game(gameId);
		if (!game.ownedBy(userId)) {
			throw new ApiException(403, "NOT_AUTHORIZED", "Só o criador pode reenviar.");
		}
		if (game.status() != GameStatus.REJECTED) {
			throw new ApiException(409, "NOT_REJECTED", "Só jogo recusado pode ser reenviado.");
		}
		return games.save(game.resubmit(clock.instant()));
	}

	/** Resolve o username da URL para o id do perfil (404 quando o perfil não existe). */
	@Transactional(readOnly = true)
	public String userIdFromUsername(String username) {
		return profiles.findByUsername(username)
			.map(com.socialcoisa.rede.profile.domain.UserProfile::id)
			.orElseThrow(() -> new ApiException(404, "GAME_NOT_FOUND", "Criador não encontrado."));
	}

	@Transactional(readOnly = true)
	public Game byUserAndSlug(String username, String slug) {
		Game game = games.findByUserAndSlug(userIdFromUsername(username), slug)
			.orElseThrow(() -> new ApiException(404, "GAME_NOT_FOUND", "Jogo não encontrado."));
		if (game.status() != GameStatus.PUBLISHED && !game.ownedBy(userIdFromUsername(username))) {
			throw new ApiException(404, "GAME_NOT_FOUND", "Jogo não encontrado.");
		}
		return game;
	}

	@Transactional(readOnly = true)
	public List<Game> publishedByUser(String username) {
		return games.findByUserAndStatus(userIdFromUsername(username), GameStatus.PUBLISHED, 50);
	}

	@Transactional(readOnly = true)
	public List<Game> ownedBy(String userId) {
		return games.findByUser(userId);
	}

	@Transactional(readOnly = true)
	public List<Game> catalog(int limit) {
		return games.findPublished(limit);
	}

	@Transactional(readOnly = true)
	public List<Game> pending(int limit) {
		return games.findByStatus(GameStatus.SUBMITTED, limit);
	}

	@Transactional
	public Game approve(String gameId, String reviewerId, String note) {
		Game game = game(gameId);
		if (game.status() != GameStatus.SUBMITTED) {
			throw new ApiException(409, "NOT_SUBMITTED", "Este jogo não está na fila de revisão.");
		}
		return games.save(game.approve(reviewerId, StringUtils.hasText(note) ? note.trim() : null, clock.instant()));
	}

	@Transactional
	public Game reject(String gameId, String reviewerId, String note) {
		Game game = game(gameId);
		if (game.status() != GameStatus.SUBMITTED) {
			throw new ApiException(409, "NOT_SUBMITTED", "Este jogo não está na fila de revisão.");
		}
		if (!StringUtils.hasText(note)) {
			throw new ApiException(400, "REVIEW_NOTE_REQUIRED", "A recusa precisa de um motivo.");
		}
		return games.save(game.reject(reviewerId, note.trim(), clock.instant()));
	}

	private Game game(String gameId) {
		return games.findById(gameId)
			.orElseThrow(() -> new ApiException(404, "GAME_NOT_FOUND", "Jogo não encontrado."));
	}

	private String slugFrom(String value) {
		return value.toLowerCase(Locale.ROOT)
			.replaceAll("[^a-z0-9]+", "-")
			.replaceAll("(^-|-$)", "");
	}

	private String uniqueSlug(String userId, String base) {
		String slug = StringUtils.hasText(base) ? base : "jogo";
		String candidate = slug;
		int n = 2;
		while (games.existsByUserAndSlug(userId, candidate)) {
			candidate = slug + "-" + n;
			n++;
		}
		return candidate;
	}
}
