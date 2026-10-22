package com.socialcoisa.rede.identity.application.port.in;

import com.socialcoisa.rede.identity.application.AuthResponse;

/**
 * Parte publica do segundo fator no login: concluir o desafio (e reenviar o codigo por e-mail).
 *
 * Fica separada da gestao ({@link TwoFactorUseCase}) porque e aqui que a sessao nasce — quem cria
 * sessao e o {@code AuthService}, e misturar as duas responsabilidades num contrato so forcaria um
 * servico a conhecer o outro.
 */
public interface TwoFactorLoginUseCase {

	AuthResponse verifyTwoFactor(String challengeId, String code);

	void resendTwoFactorCode(String challengeId);
}