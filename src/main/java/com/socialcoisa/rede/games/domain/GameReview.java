package com.socialcoisa.rede.games.domain;

import java.time.Instant;

/** Avaliação de um jogo: uma por pessoa, nota de 1 a 5 e um texto opcional. */
public record GameReview(String id, String gameId, String userId, int rating, String body,
		Instant createdAt, Instant updatedAt) {

	public static GameReview create(String id, String gameId, String userId, int rating, String body, Instant now) {
		return new GameReview(id, gameId, userId, rating, body, now, now);
	}

	public GameReview update(int rating, String body, Instant now) {
		return new GameReview(id, gameId, userId, rating, body, createdAt, now);
	}
}
