package com.socialcoisa.rede.linkedin.domain;

import java.time.Instant;

/**
 * Conexao de publicacao de uma pessoa com o LinkedIn.
 *
 * O token nunca e guardado em claro: `accessTokenEnc` e a saida do SecretCipher, e o servico so
 * grava a linha quando a chave de criptografia esta configurada. `memberSub` e o `sub` do OIDC do
 * LinkedIn — e com ele que a API identifica o autor (`urn:li:person:{sub}`).
 */
public record LinkedInConnection(String userId, String memberSub, String accessTokenEnc,
		Instant tokenExpiresAt, boolean postSync, Instant createdAt, Instant updatedAt) {

	public LinkedInConnection withPostSync(boolean enabled, Instant now) {
		return new LinkedInConnection(userId, memberSub, accessTokenEnc, tokenExpiresAt, enabled, createdAt, now);
	}

	public boolean tokenValid(Instant now) {
		return tokenExpiresAt != null && tokenExpiresAt.isAfter(now);
	}
}
