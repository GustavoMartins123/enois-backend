package com.socialcoisa.rede.identity.application;

import java.time.Instant;

/**
 * Resultado do login: ou a sessao, ou o desafio do segundo fator.
 *
 * Modelado como um dos dois (e nao como uma sessao com um campo "falta o 2FA") para que seja
 * impossivel entregar token sem passar pelo codigo: quem tem segundo fator ativo so recebe sessao em
 * {@code verifyTwoFactor}.
 */
public record LoginResult(AuthResponse session, TwoFactorChallengeView challenge) {

	public static LoginResult authenticated(AuthResponse session) {
		return new LoginResult(session, null);
	}

	public static LoginResult twoFactorRequired(String challengeId, String method, Instant expiresAt) {
		return new LoginResult(null, new TwoFactorChallengeView(challengeId, method, expiresAt));
	}

	public boolean requiresTwoFactor() {
		return challenge != null;
	}

	public record TwoFactorChallengeView(String challengeId, String method, Instant expiresAt) {
	}
}