package com.socialcoisa.rede.games.domain;

import java.time.Instant;

/**
 * Um evento registrado do jogo (append-only): visualização da página, início de partida e
 * compartilhamento. É a matéria-prima dos números e do gráfico de uso do painel.
 */
public record GameEvent(String id, String gameId, GameEventKind kind, String userId, Instant createdAt) {
}
