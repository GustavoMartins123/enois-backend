package com.socialcoisa.rede.support.domain;

/**
 * Categoria do pedido de suporte. BUG é o que o painel mostra na aba "Issues".
 *
 * API_ACCESS é o pedido de acesso à API de produção: a API pública roda no sandbox e a liberação
 * passa por análise (identificação do responsável + tipo de integração). O pedido chega como
 * ticket normal, com a categoria certa, para o time achar e responder.
 */
public enum SupportCategory {
	BUG,
	ACCOUNT,
	CONTENT,
	IDEA,
	API_ACCESS,
	OTHER
}
