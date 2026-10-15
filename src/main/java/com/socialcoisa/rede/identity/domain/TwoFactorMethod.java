package com.socialcoisa.rede.identity.domain;

public enum TwoFactorMethod {

	/** Codigo do aplicativo authenticator (TOTP). */
	TOTP,

	/** Codigo de 6 digitos enviado por e-mail. */
	EMAIL
}