package com.socialcoisa.rede.games.application;

import java.util.List;

/**
 * Uma página do catálogo: os itens e o cursor da próxima.
 *
 * O cursor é o deslocamento da próxima página (`"24"`), e `null` quer dizer que acabou — a tela não
 * precisa de um segundo pedido só para saber se há mais.
 */
public record GamePageView(List<GameView> items, String nextCursor) {
}
