package com.socialcoisa.rede.games.domain;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.util.StringUtils;

import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Coleção temática de jogos (G3 do plano): curadoria da comunidade — a lista curada
 * aparece como um bloco no catálogo. A lista de jogos viaja como CSV (a convenção das
 * tags) e os jogos resolvidos saem na leitura.
 */
public record GameCollection(String id, String curatorId, String title, String description, List<String> gameIds,
		Status status, Instant createdAt, Instant updatedAt) {

	public enum Status {
		PUBLISHED, HIDDEN
	}

	public static GameCollection create(String curatorId, String title, String description, List<String> gameIds,
			Instant now) {
		if (!StringUtils.hasText(title) || title.trim().length() < 5) {
			throw new ApiException(400, "TITLE_REQUIRED", "O título precisa de pelo menos 5 caracteres.");
		}
		List<String> ids = gameIds == null ? List.of()
				: gameIds.stream().filter(StringUtils::hasText).map(String::trim).distinct().toList();
		if (ids.isEmpty()) {
			throw new ApiException(400, "GAMES_REQUIRED", "A coleção precisa de pelo menos um jogo.");
		}
		return new GameCollection("gcol_" + UUID.randomUUID().toString().replace("-", ""), curatorId, title.trim(),
			description == null ? null : description.trim(), ids, Status.PUBLISHED, now, now);
	}

	public boolean ownedBy(String accountId) {
		return curatorId != null && curatorId.equals(accountId);
	}

	public GameCollection hide(Instant now) {
		return new GameCollection(id, curatorId, title, description, gameIds, Status.HIDDEN, createdAt, now);
	}

	/** Os ids como a coluna guarda (CSV — a convenção das tags). */
	public String idsAsCsv() {
		return String.join(",", gameIds);
	}

	public static List<String> idsFromCsv(String csv) {
		return csv == null || csv.isBlank() ? List.of()
				: Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
	}
}
