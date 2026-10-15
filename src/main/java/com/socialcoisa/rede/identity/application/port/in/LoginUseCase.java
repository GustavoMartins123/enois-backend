package com.socialcoisa.rede.identity.application.port.in;

import com.socialcoisa.rede.identity.application.LoginCommand;
import com.socialcoisa.rede.identity.application.LoginResult;

public interface LoginUseCase {

	/**
	 * Autentica por senha. Quando a conta tem segundo fator, devolve o desafio em vez da sessao —
	 * o token so sai depois de {@code POST /auth/2fa/verify}.
	 */
	LoginResult login(LoginCommand command);
}