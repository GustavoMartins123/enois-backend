package com.socialcoisa.rede.identity.application.port.in;

public interface ChangeEmailUseCase {

	void changeEmail(String sessionToken, String newEmail, String password);

	/** Confirma o NOVO endereço a partir do link enviado para ele (o endereço só vale aqui). */
	void confirmEmailChange(String token);
}