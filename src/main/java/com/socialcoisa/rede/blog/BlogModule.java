package com.socialcoisa.rede.blog;

/**
 * Modulo do blog pessoal (enois.ia.br/blog/{username}).
 *
 * Um blog por pessoa, com posts que nao entram no feed global: quem quiser acompanhar se inscreve, e
 * so entao as novidades aparecem no "Para voce" de quem segue (nunca no "Tudo") e geram notificacao.
 * O post do blog vira um post-ponteiro no feed com a tag "blog" — o conteudo mora aqui, no cantinho
 * do autor, que pode ser HTML/CSS proprio ou markdown simples.
 */
public final class BlogModule {

	private BlogModule() {
	}
}
