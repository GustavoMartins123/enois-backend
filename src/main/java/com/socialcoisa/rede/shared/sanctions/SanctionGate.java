package com.socialcoisa.rede.shared.sanctions;

import org.springframework.stereotype.Component;

import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Portão de entrada do enforcement da sanção de moderação.
 *
 * <p>Fica em {@code shared} pelo mesmo motivo do {@link com.socialcoisa.rede.shared.age.AgeGate}:
 * feed, grupos, conversa e blog precisam da mesma resposta sem depender do módulo de moderação,
 * que é o dono da escrita. O código e a mensagem são únicos, para o front tratar o caso igual em
 * qualquer ponto do app.
 *
 * <p><b>Por que existe:</b> a restrição é uma decisão humana ("não publica nem comenta"). Em
 * 2026-10-21 ela era conferida só no post e no comentário do feed: a mesma conta restrita ainda
 * publicava em grupo, comentava em grupo e mandava mensagem privada — ou seja, continuava
 * alcançando pessoas (e o canal privado é justamente onde a moderação não enxerga).
 */
@Component
public class SanctionGate {

	public static final String CODE = "ACCOUNT_RESTRICTED";

	private static final String MESSAGE = "Sua conta está restrita por moderação. Você pode recorrer na página Penalidades.";

	private final SanctionGateReader reader;

	public SanctionGate(SanctionGateReader reader) {
		this.reader = reader;
	}

	/**
	 * Ação de escrita exige conta sem restrição ativa.
	 *
	 * <p>Vale para todo caminho que produz conteúdo ou fala com outra pessoa: post, comentário,
	 * post de grupo, comentário de grupo e mensagem privada. A conta banida nem chega aqui (a
	 * sessão é revogada no banimento); a restrita continua conectada, e é por isso que o portão
	 * precisa existir.
	 */
	public void requireNotRestricted(String userId) {
		if (reader.restricted(userId)) {
			throw new ApiException(403, CODE, MESSAGE);
		}
	}
}
