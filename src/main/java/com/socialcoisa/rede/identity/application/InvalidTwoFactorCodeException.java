package com.socialcoisa.rede.identity.application;

import com.socialcoisa.rede.shared.web.ApiException;

/** Only this rejected verification commits its failed-attempt counter; other failures roll back. */
final class InvalidTwoFactorCodeException extends ApiException {
	InvalidTwoFactorCodeException() {
		super(400, "INVALID_TWO_FACTOR_CODE", "Codigo incorreto.");
	}
}
