package com.socialcoisa.rede.identity.application.port.out;

import java.util.Optional;

import com.socialcoisa.rede.identity.domain.EmailVerificationToken;

public interface EmailVerificationTokenRepository {

	void save(EmailVerificationToken token);

	Optional<EmailVerificationToken> findByToken(String token);

	void markUsed(String token);

	/**
	 * Invalida os tokens anteriores do usuario. Marcamos como usados em vez de apagar: o link antigo
	 * passa a responder "ja usado" e as linhas continuam servindo de contagem para o limite de
	 * reenvio (apagando, a contagem nunca passaria de 1).
	 */
	int markAllUsedByUserId(String userId, java.time.Instant now);

	/** Quantos tokens o usuario pediu desde um instante (limite de reenvio). */
	int countByUserIdSince(String userId, java.time.Instant since);
}