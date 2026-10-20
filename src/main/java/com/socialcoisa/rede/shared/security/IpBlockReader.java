package com.socialcoisa.rede.shared.security;

/**
 * Lista de IPs bloqueados pela moderacao (V115). Um IP bloqueado nao cria conta, nao loga e nao
 * emite sessao — barrado no controller de autenticacao antes de qualquer verificacao de senha.
 *
 * <p>Fica em {@code shared} pelo mesmo motivo do {@code SanctionGateReader}: a identidade precisa
 * da resposta e a moderacao e quem dona da lista.
 */
public interface IpBlockReader {

	/** O IP tem bloqueio ativo? IP nulo/vazio/nao informado nunca bloqueia. */
	boolean blocked(String ip);
}
