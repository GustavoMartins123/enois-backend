package com.socialcoisa.rede.identity.application.port.out;

import java.util.Optional;

import com.socialcoisa.rede.identity.domain.IdentityVerificationStatus;

/**
 * Provedor de verificacao de identidade. A regra de negocio nao conhece fornecedor: liga-se a esta
 * porta. Em producao, sem provedor autorizado configurado, nenhum provedor esta disponivel e a
 * verificacao fica indisponivel — nunca aprovada por simulacao.
 */
public interface IdentityVerificationProvider {

	String name();

	/** Inicia uma verificacao e devolve a tentativa e o destino para o usuario concluir no provedor. */
	Start start(String userId, String consentVersion);

	/** Consulta o desfecho confirmado pelo provedor para uma tentativa (nao confia no navegador). */
	Optional<Outcome> fetchOutcome(String attemptId);

	/** Cancela/limpa uma tentativa pendente, quando suportado. */
	default void cancel(String attemptId) {
	}

	record Start(String attemptId, String redirectUrl) {
	}

	record Outcome(IdentityVerificationStatus status, String reasonCode) {
	}
}
