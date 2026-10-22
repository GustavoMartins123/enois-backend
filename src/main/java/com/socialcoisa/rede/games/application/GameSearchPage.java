package com.socialcoisa.rede.games.application;

import java.util.List;

import com.socialcoisa.rede.games.domain.Game;

/**
 * Uma página do catálogo vinda do banco: os itens e se existe próxima.
 *
 * `hasMore` sai de um item a mais na consulta (`Slice` do Spring Data) — a alternativa seria uma
 * segunda consulta de contagem, que custa mais e pode discordar do que foi listado.
 */
public record GameSearchPage(List<Game> items, boolean hasMore) {
}
