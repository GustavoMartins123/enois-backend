package com.socialcoisa.rede.messaging.application;

import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Contact refused before any write. It does not mark a caller's transaction rollback-only, so
 * best-effort deliveries (job application DMs) can catch it; HTTP callers still roll back.
 */
final class ContactRestrictedException extends ApiException {
	ContactRestrictedException(String code, String message) {
		super(403, code, message);
	}
}
