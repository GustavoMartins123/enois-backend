package com.socialcoisa.rede.feed.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * A identidade de autoria da publicação. Regras: toda publicação tem uma (nunca nula), o
 * padrão é SOCIAL, e editar o conteúdo/tipo NUNCA muda a identidade — a autoria acompanha a
 * publicação para sempre.
 */
class PostAuthorIdentityTests {

	private final Instant agora = Instant.parse("2026-10-20T12:00:00Z");

	private Post post(PostType type, AuthorIdentity identity) {
		return Post.publish(new PostId("post_1"), new UserId("user_1"), type, "texto", null, List.of(),
			List.of(), null, agora, identity);
	}

	@Test
	void publicacaoSocialSemicolheSOCIALPorPadrao() {
		assertThat(Post.publish(new PostId("post_1"), new UserId("user_1"), PostType.INSIGHT, "texto", null,
			List.of(), List.of(), null, agora).authorIdentity()).isEqualTo(AuthorIdentity.SOCIAL);
	}

	@Test
	void businessECorporate() {
		assertThat(post(PostType.BUSINESS, AuthorIdentity.CORPORATE).authorIdentity())
			.isEqualTo(AuthorIdentity.CORPORATE);
	}

	@Test
	void identidadeNulaNuncaExiste() {
		// Nenhum caminho de criação produz post sem identidade: nulo vira SOCIAL.
		Post semIdentidade = Post.restore(new PostId("post_1"), new UserId("user_1"), PostType.INSIGHT, "texto",
			null, List.of(), List.of(), null, agora, Visibility.PUBLIC, new Counters(0, 0, 0, 0),
			PostStatus.ACTIVE, null, null, null, null);
		assertThat(semIdentidade.authorIdentity()).isEqualTo(AuthorIdentity.SOCIAL);
	}

	@Test
	void editarPreservaAIdentidade() {
		Post social = post(PostType.INSIGHT, AuthorIdentity.SOCIAL);
		Post corporativo = post(PostType.BUSINESS, AuthorIdentity.CORPORATE);

		Post socialEditado = social.edited("novo texto", null, List.of(), agora.plusSeconds(60));
		Post corporativoEditado = corporativo.edited(PostType.INSIGHT, "texto virado social?",
			null, List.of(), agora.plusSeconds(60));

		assertThat(socialEditado.authorIdentity()).isEqualTo(AuthorIdentity.SOCIAL);
		// A identidade acompanha a publicação mesmo que alguém tente trocar o tipo no meio.
		assertThat(corporativoEditado.authorIdentity()).isEqualTo(AuthorIdentity.CORPORATE);
	}
}
