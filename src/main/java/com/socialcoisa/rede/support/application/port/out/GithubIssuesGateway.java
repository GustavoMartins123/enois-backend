package com.socialcoisa.rede.support.application.port.out;

import java.util.Optional;

/**
 * Espelho dos pedidos de suporte em issues do GitHub (repositorio privado). Sem token/repo
 * configurados o gateway fica desligado e tudo vira no-op: o suporte continua funcionando só com
 * e-mail e painel.
 */
public interface GithubIssuesGateway {

	boolean enabled();

	/** Cria a issue do pedido; vazio quando o GitHub nao respondeu (falha nao derruba o ticket). */
	Optional<IssueRef> createIssue(String title, String body);

	boolean closeIssue(int issueNumber);

	boolean reopenIssue(int issueNumber);

	record IssueRef(int number, String url) {
	}
}
