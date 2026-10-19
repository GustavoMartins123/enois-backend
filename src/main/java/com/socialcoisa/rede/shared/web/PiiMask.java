package com.socialcoisa.rede.shared.web;

/**
 * Mascara dados pessoais em respostas de painel. O operador precisa reconhecer o destino
 * (domínio e primeira letra), mas não precisa ver o endereço completo de quem usa a plataforma.
 */
public final class PiiMask {

	private PiiMask() {
	}

	public static String email(String value) {
		if (value == null || value.isBlank()) {
			return value;
		}
		String trimmed = value.trim();
		int at = trimmed.indexOf('@');
		if (at <= 0 || at == trimmed.length() - 1) {
			return "***";
		}
		String local = trimmed.substring(0, at);
		String domain = trimmed.substring(at);
		return (local.length() <= 1 ? "***" : local.charAt(0) + "***") + domain;
	}
}
