package com.socialcoisa.rede.stats.application;

import java.time.Instant;

/**
 * Numeros publicos da comunidade, para a landing.
 *
 * `activeUsers` e o numero que o produto chama de "usuarios ativos": quem usou a plataforma nos
 * ultimos {@code windowDays} dias. "Usou" e uma definicao explicita, nao um chute — sessao viva
 * (o refresh de 30 dias e renovado a cada acesso) ou publicacao/comentario no periodo. Nao entra
 * aqui quem apenas se cadastrou e nunca voltou.
 */
public record CommunityStats(long activeUsers, long totalUsers, int windowDays, Instant computedAt) {
}
