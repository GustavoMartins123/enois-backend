package com.socialcoisa.rede.identity.application.port.out;

/**
 * Dados confiaveis extraidos do id_token depois de conferir assinatura, emissor, audience, validade
 * e nonce.
 *
 * @param externalUserId identificador do usuario no provedor (claim `sub`)
 * @param email          e-mail informado pelo provedor (pode vir vazio em logins Apple seguintes)
 * @param name           nome informado pelo provedor (idem)
 * @param emailVerified  o provedor afirma que o e-mail e verificado — nunca inferido do que o
 *                       cliente mandou
 */
public record TokenValidationResult(String externalUserId, String email, String name, boolean emailVerified) {

	public TokenValidationResult(String externalUserId, String email, String name) {
		this(externalUserId, email, name, true);
	}

	public TokenValidationResult {
		if (externalUserId == null || externalUserId.isBlank()) {
			throw new IllegalArgumentException("externalUserId is required");
		}
	}

	public String email() {
		return email == null ? "" : email;
	}

	public String name() {
		return name == null ? "" : name;
	}
}
