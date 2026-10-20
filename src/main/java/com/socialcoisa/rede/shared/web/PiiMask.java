package com.socialcoisa.rede.shared.web;

/**
 * Remove dados pessoais das respostas de painel. Endereço de e-mail não é necessário para
 * operar a fila (o envio e a resposta usam o registro no banco), então o painel recebe apenas
 * um marcador — nem inicial nem domínio.
 */
public final class PiiMask {

	private static final java.util.regex.Pattern EMAIL =
		java.util.regex.Pattern.compile("[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}");

	private PiiMask() {
	}

	public static String email(String value) {
		return value == null || value.isBlank() ? value : "***";
	}

	/** Texto livre (assunto/erro de envio) pode carregar um endereço colado; ele também sai. */
	public static String text(String value) {
		return value == null ? null : EMAIL.matcher(value).replaceAll("***");
	}
}
