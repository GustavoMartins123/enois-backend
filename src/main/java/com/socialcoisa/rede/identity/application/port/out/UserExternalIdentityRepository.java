package com.socialcoisa.rede.identity.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserExternalIdentityRepository {

	void save(String userId, String provider, String externalUserId, String email);

	Optional<String> findUserIdByProviderAndExternalId(String provider, String externalUserId);

	boolean existsByProviderAndExternalId(String provider, String externalUserId);

	/** Provedores ligados a esta conta (tela de vinculos e decisao sobre desvincular). */
	List<LinkedIdentity> findByUserId(String userId);

	/** Remove o vinculo de um provedor especifico, preservando os demais. */
	void deleteByUserIdAndProvider(String userId, String provider);

	void deleteByUserId(String userId);

	record LinkedIdentity(String provider, String email, Instant createdAt) {
	}
}
