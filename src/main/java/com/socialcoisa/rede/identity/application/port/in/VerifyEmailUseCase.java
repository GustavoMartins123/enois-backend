package com.socialcoisa.rede.identity.application.port.in;

import com.socialcoisa.rede.identity.application.AuthResponse;

public interface VerifyEmailUseCase {

	/**
	 * Confirma o e-mail a partir do token do link, marca a conta como verificada e ja devolve uma
	 * sessao (quem confirma nao precisa entrar de novo).
	 */
	AuthResponse verifyEmail(String token);

	/** Reenvia o link de confirmacao. Nao revela se o e-mail existe. */
	void resendVerification(String email);
}