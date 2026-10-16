package com.socialcoisa.rede.identity.application.port.out;

import java.util.Optional;

import com.socialcoisa.rede.identity.domain.IdentityVerification;

public interface IdentityVerificationRepository {

	Optional<IdentityVerification> findByUserId(String userId);

	/** Localiza pela tentativa/state (callback do provedor chega sem sessao). */
	Optional<IdentityVerification> findByAttemptId(String attemptId);

	void save(IdentityVerification verification);
}
