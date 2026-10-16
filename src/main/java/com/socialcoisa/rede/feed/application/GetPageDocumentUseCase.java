package com.socialcoisa.rede.feed.application;

import com.socialcoisa.rede.feed.domain.PostId;

/**
 * Documento HTML de uma pagina publicada, para ser servido na ORIGEM DE CONTEUDO.
 *
 * Existe separado do `getPost` de proposito: quem pede o documento quer o HTML para renderizar num
 * quadro, nao a view do post (autor, contadores, reacoes). Servir a view aqui daria dado a mais para
 * quem so quer desenhar a pagina.
 */
public interface GetPageDocumentUseCase {

	/** HTML ja sanitizado da pagina. Lanca 404 se nao for uma pagina publicada. */
	PageDocument getPageDocument(PostId postId);

	record PageDocument(String postId, String html) {
	}
}
