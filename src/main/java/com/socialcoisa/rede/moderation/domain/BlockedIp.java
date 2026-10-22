package com.socialcoisa.rede.moderation.domain;

import java.time.Instant;

/** Um IP bloqueado pela moderacao. A unicidade do IP garante a lista. */
public record BlockedIp(String id, String ip, String reason, String createdBy, Instant createdAt) {

	public static BlockedIp create(String id, String ip, String reason, String createdBy, Instant now) {
		String clean = cleanIp(ip);
		if (clean.isEmpty() || clean.length() > 64) {
			throw new com.socialcoisa.rede.shared.web.ApiException(400, "INVALID_IP",
				"Informe um IP valido para bloquear.");
		}
		return new BlockedIp(id, clean, trim(reason), trim(createdBy), now);
	}

	public static String cleanIp(String ip) {
		return ip == null ? "" : ip.trim();
	}

	private static String trim(String value) {
		return value == null ? null : value.trim();
	}
}
