package com.socialcoisa.rede.identity.application.port.out;

import java.util.Optional;

public interface CredentialRepository {

	/**
	 * @param passwordUsable false quando a senha e aleatoria e o usuario nao a conhece (conta criada
	 *                       por login social) — e o que impede desvincular o provedor e trancar a
	 *                       pessoa fora da propria conta
	 */
	void save(String userId, String email, String passwordHash, boolean passwordUsable);

	default void save(String userId, String email, String passwordHash) {
		save(userId, email, passwordHash, true);
	}

	Optional<StoredCredential> findByEmail(String email);

	Optional<StoredCredential> findByUserId(String userId);

	boolean existsByEmail(String email);

	void deleteByUserId(String userId);
}
