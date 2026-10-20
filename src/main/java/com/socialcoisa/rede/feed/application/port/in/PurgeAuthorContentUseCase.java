package com.socialcoisa.rede.feed.application.port.in;

/**
 * Purga TOTAL do conteudo de um autor: apaga do banco (hard delete) todos os posts que ele
 * publicou e os comentarios que ele escreveu em posts de outras pessoas, junto com reacoes,
 * selos, salvamentos, compartilhamentos, tags, midias anexadas e o corpo de artigos.
 *
 * <p>Diferente da exclusao normal (soft delete com deletedAt) e da remocao por moderacao (que
 * tambem e soft delete): aqui a linha sai do banco de verdade — uso para contas banidas por spam,
 * onde o conteudo nao pode sobrar nem para auditoria de tela.
 */
public interface PurgeAuthorContentUseCase {

	PurgeResult purgeAllByAuthor(String actorId, String authorId, String reason);

	record PurgeResult(int posts, int comments, int otherRows) {
	}
}
