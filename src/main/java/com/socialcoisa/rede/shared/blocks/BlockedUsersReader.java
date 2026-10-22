package com.socialcoisa.rede.shared.blocks;

import java.util.Collections;
import java.util.Set;

/**
 * Leitura dos usuarios escondidos para um visitante (bloqueio nos dois sentidos).
 *
 * Fica em `shared` porque feed, notificacoes, busca e rede precisam da mesma resposta; a escrita
 * (bloquear/desbloquear) continua no modulo de rede, que e dono do assunto.
 */
public interface BlockedUsersReader {

	/** Ids que nao devem aparecer para este usuario. Vazio quando nao ha sessao nem bloqueios. */
	Set<String> hiddenIdsFor(String userId);

	/** Implementacao neutra para quem precisa do tipo sem depender do modulo de rede. */
	static BlockedUsersReader none() {
		return userId -> Collections.emptySet();
	}
}
