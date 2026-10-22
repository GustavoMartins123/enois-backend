package com.socialcoisa.rede.feed.application;

import java.time.Instant;
import java.util.List;

import com.socialcoisa.rede.feed.domain.PostSealType;

/**
 * Quem marcou o post e com o que — a lista que a tela abre ao clicar num selo (o mesmo caminho da
 * lista de quem reagiu).
 *
 * `seals` repete a contagem por selo, para o topo do dialogo responder "quantos selos o post tem"
 * sem uma segunda chamada. Quem esta lendo nao aparece diferente dos outros: o front ja sabe quais
 * selos sao dele (`mySeals` na leitura em lote) e marca a linha.
 */
public record SealPeopleView(String postId, List<SealsView.SealCount> seals, List<PersonSeal> people) {

	/** Um selo e quem deu. A ordem e a de chegada (mais antigo primeiro). */
	public record PersonSeal(String userId, PostSealType seal, Instant createdAt) {
	}
}
