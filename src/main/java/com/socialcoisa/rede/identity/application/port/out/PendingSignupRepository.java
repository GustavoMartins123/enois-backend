package com.socialcoisa.rede.identity.application.port.out;

import java.time.Instant;
import java.util.Optional;

import com.socialcoisa.rede.identity.domain.PendingSignup;

/** Cadastros aguardando confirmacao de e-mail (a conta ainda nao existe). */
public interface PendingSignupRepository {

	void save(PendingSignup signup);

	Optional<PendingSignup> findByTokenHash(String tokenHash);

	Optional<PendingSignup> findByEmail(String email);

	void deleteByEmail(String email);

	/** Limpeza oportunista: pendentes vencidos nao servem para nada e nao devem acumular. */
	void deleteExpired(Instant now);
}
