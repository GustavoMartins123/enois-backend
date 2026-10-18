package com.socialcoisa.rede.groups.domain;

import java.text.Normalizer;
import java.util.Locale;

/**
 * O endereço do grupo a partir do nome.
 *
 * Acento vira letra (é → e) em vez de sumir: sem isso "Café & Cia" viraria "caf-cia", que não é o
 * nome de ninguém. O resultado é sempre minúsculo, com hífen no lugar do que não é letra ou número,
 * e nunca vazio — nome só de símbolos cai em "grupo". O teto é o da coluna (80), cortado sem deixar
 * hífen pendurado.
 */
public final class GroupSlug {

	private static final int MAX_LENGTH = 80;
	private static final String FALLBACK = "grupo";

	private GroupSlug() {
	}

	public static String from(String name) {
		String slug = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD)
			.replaceAll("\\p{M}+", "")
			.toLowerCase(Locale.ROOT)
			.replaceAll("[^a-z0-9]+", "-")
			.replaceAll("(^-|-$)", "");
		if (slug.isEmpty()) {
			return FALLBACK;
		}
		if (slug.length() > MAX_LENGTH) {
			slug = slug.substring(0, MAX_LENGTH).replaceAll("-+$", "");
		}
		return slug;
	}
}
