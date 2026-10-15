package com.socialcoisa.rede.identity.application.port.out;

import java.util.Optional;

import com.socialcoisa.rede.identity.domain.IdentityVerification;

public interface IdentityVerificationRepository {

	Optional<IdentityVerification> findByUserId(String userId);

	void save(IdentityVerification verification);
}
