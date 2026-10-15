package com.socialcoisa.rede.identity.application.port.out;

import java.util.Optional;

import com.socialcoisa.rede.identity.domain.OAuthProvider;

public interface TokenValidator {

	/**
	 * Valida a credencial devolvida pelo provedor (codigo de autorizacao ou id_token).
	 *
	 * @param expectedNonce nonce emitido no inicio do fluxo: o id_token precisa trazer exatamente
	 *                      este valor, senao o token pode ter sido emitido para outro pedido
	 */
	Optional<TokenValidationResult> validate(OAuthProvider provider, String credential, String expectedNonce);
}
