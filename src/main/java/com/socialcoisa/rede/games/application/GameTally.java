package com.socialcoisa.rede.games.application;

/**
 * Quantas sessões e quantas pessoas jogaram um jogo na janela pedida.
 *
 * Os dois números ficam separados porque significam coisas diferentes: dez sessões de uma pessoa
 * curiosa não são dez pessoas. `players` segue a mesma estimativa do resto do catálogo — quem tem
 * sessão conta uma vez, anônimo conta por evento.
 */
public record GameTally(String gameId, long plays, long players) {
}
