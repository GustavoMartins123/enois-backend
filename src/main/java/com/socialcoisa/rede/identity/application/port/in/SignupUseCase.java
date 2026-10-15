package com.socialcoisa.rede.identity.application.port.in;

import com.socialcoisa.rede.identity.application.SignupCommand;
import com.socialcoisa.rede.identity.application.SignupResult;

public interface SignupUseCase {

	/**
	 * Cria a conta em estado pendente e envia o link de confirmacao para o e-mail informado.
	 * A sessao so e emitida depois que o e-mail for confirmado.
	 */
	SignupResult signup(SignupCommand command);
}