package com.socialcoisa.rede.feed.domain;

/**
 * Selos que a comunidade deixa no topo de um post — a "sinalizacao" ao lado da reacao.
 *
 * Nove selos, escolhidos para cobrir o que as pessoas querem dizer sobre um post sem escrever um
 * comentario: o que presta (importante, interessante, dica, ajuda, boa pergunta, inspirador) e o que
 * nao presta (post duplicado, fanfic, massante). Um por pessoa por post.
 *
 * Os rotulos e os icones vivem no front (constants/post-seals.ts) — aqui so o identificador, que e
 * o que vai e volta pela API.
 */
public enum PostSealType {
	IMPORTANTE,
	INTERESSANTE,
	DICA,
	AJUDA,
	DUPLICADO,
	FANFIC,
	MASSANTE,
	BOA_PERGUNTA,
	INSPIRADOR
}
