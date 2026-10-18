package com.socialcoisa.rede.games.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.socialcoisa.rede.games.application.port.out.GameRepository;
import com.socialcoisa.rede.games.domain.GameStatus;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * A IDENTIDADE DO JOGADOR DENTRO DO JOGO — nunca a sessão do Énóis.
 *
 * O jogo roda num iframe de outra origem e JAMAIS recebe o token de sessão. Quando a pessoa
 * joga, a plataforma emite:
 * <ul>
 *   <li>um <b>token opaco</b> (32 bytes aleatórios, guardado só como HASH, 15 min, uso ÚNICO)
 *   que o jogo troca no próprio servidor pela identidade;</li>
 *   <li>um <b>playerRef</b> estável e pseudônimo (HMAC do usuário+jogo) — é com ele que o jogo
 *   cria a conta DELE e reconhece a mesma pessoa entre sessões, sem nunca saber quem ela é no
 *   Énóis.</li>
 * </ul>
 * Gasto de faísca NÃO autentica por aqui: o jogo só PEDE (postMessage), e a transação é
 * confirmada pela pessoa na plataforma (ver o endpoint de despesas).
 */
@Service
public class GameIdentityService {

	/** Quanto tempo o jogo tem para trocar o token no servidor dele. */
	public static final Duration TOKEN_TTL = Duration.ofMinutes(15);

	private final GameTokenRepository tokens;
	private final GameRepository games;
	private final Clock clock;

	public GameIdentityService(GameTokenRepository tokens, GameRepository games, Clock clock) {
		this.tokens = tokens;
		this.games = games;
		this.clock = clock;
	}

	public record IssuedToken(String token, Instant expiresAt) {
	}

	public record PlayerIdentity(String gameId, String playerRef) {
	}

	/** Emite o token para UMA partida (só jogo publicado; sessão do Énóis obrigatória). */
	@Transactional
	public IssuedToken mint(String userId, String gameId) {
		var game = games.findById(gameId)
			.orElseThrow(() -> new ApiException(404, "GAME_NOT_FOUND", "Jogo não encontrado."));
		if (game.status() != GameStatus.PUBLISHED) {
			throw new ApiException(409, "GAME_NOT_PUBLISHED", "Só jogo publicado identifica jogador.");
		}
		byte[] raw = new byte[32];
		new SecureRandom().nextBytes(raw);
		String token = HexFormat.of().formatHex(raw);
		Instant now = clock.instant();
		tokens.save(new GameToken("gtok_" + java.util.UUID.randomUUID().toString().replace("-", ""), gameId,
			userId, sha256(token), playerRef(userId, gameId), now.plus(TOKEN_TTL), null, now));
		return new IssuedToken(token, now.plus(TOKEN_TTL));
	}

	/**
	 * Validação do lado do JOGO (servidor dele): token → identidade pseudônima. Uso único:
	 * o mesmo token não vale duas vezes; expirado também não.
	 */
	@Transactional
	public PlayerIdentity validate(String token) {
		if (token == null || token.isBlank()) {
			throw new ApiException(400, "TOKEN_REQUIRED", "Informe o token da identidade.");
		}
		GameToken found = tokens.findByHash(sha256(token.trim()))
			.orElseThrow(() -> new ApiException(404, "TOKEN_INVALID", "Token inválido."));
		Instant now = clock.instant();
		if (found.usedAt() != null) {
			throw new ApiException(409, "TOKEN_ALREADY_USED", "Este token já foi trocado.");
		}
		if (!found.expiresAt().isAfter(now)) {
			throw new ApiException(410, "TOKEN_EXPIRED", "Token expirado — peça outro na plataforma.");
		}
		tokens.save(found.consume(now));
		return new PlayerIdentity(found.gameId(), found.playerRef());
	}

	private String sha256(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * O pseudônimo do jogador NAQUELE jogo: estável (mesma pessoa = mesmo ref) e sem relação
	 * visível com o id do Énóis (HMAC com a chave da instalação).
	 */
	public String playerRef(String userId, String gameId) {
		return "plr_" + sha256("game-player:" + userId + ":" + gameId).substring(0, 32);
	}

	public Optional<GameToken> byHash(String hash) {
		return tokens.findByHash(hash);
	}

	public interface GameTokenRepository {

		void save(GameToken token);

		Optional<GameToken> findByHash(String hash);
	}

	public record GameToken(String id, String gameId, String userId, String tokenHash, String playerRef,
			Instant expiresAt, Instant usedAt, Instant createdAt) {
		public GameToken consume(Instant now) {
			return new GameToken(id, gameId, userId, tokenHash, playerRef, expiresAt, now, createdAt);
		}
	}
}
