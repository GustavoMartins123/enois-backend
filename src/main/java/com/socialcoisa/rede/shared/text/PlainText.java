package com.socialcoisa.rede.shared.text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.util.StringUtils;

/**
 * Defesa contra texto patologico ("travazap").
 *
 * O ataque classico nao e um exploit: e texto. Milhares de caracteres invisiveis ou de controle de
 * direcao (bidi), marcas de combinacao empilhadas e repeticoes gigantes fazem o renderizador (e o
 * aparelho de quem le) engasgar. O limite de tamanho sozinho nao resolve: dentro de 1.200
 * caracteres cabe muito caractere de controle.
 *
 * Aqui a gente tira o que nao e texto de verdade e mantem o que e:
 *
 * - caracteres de controle de direcao e marcas invisiveis saem (bidi, zero-width, BOM). Letras
 *   arabicas, hebraicas e emojis FICAM — quem escreve em arabe continua escrevendo em arabe;
 * - marcas de combinacao (acentos que se empilham sobre a mesma letra) sao limitadas a
 *   {@code MAX_COMBINING} por caractere, que e o que o uso legitimo precisa;
 * - repeticoes enormes do mesmo caractere viram {@code MAX_REPEAT}, para "aaaaaaaa..." nao ocupar
 *   a tela inteira.
 */
public final class PlainText {

	/** Marcas de combinacao aceitas por caractere base (uso legitimo nao passa de 3 ou 4). */
	private static final int MAX_COMBINING = 5;

	/** Repeticao maxima do mesmo caractere. */
	private static final int MAX_REPEAT = 12;

	private static final Pattern INVISIBLE = Pattern.compile(
		"[\\u200B-\\u200F\\u202A-\\u202E\\u2066-\\u2069\\u061C\\u00AD\\uFEFF]");
	private static final Pattern COMBINING_RUN = Pattern.compile("\\p{M}{6,}");
	private static final Pattern REPEATED = Pattern.compile("(.)\\1{12,}");

	private PlainText() {
	}

	/** Devolve o texto sem os truques que travam a leitura. Texto vazio continua vazio. */
	public static String defuse(String raw) {
		if (!StringUtils.hasText(raw)) {
			return raw;
		}

		String clean = INVISIBLE.matcher(raw).replaceAll("");
		clean = collapse(clean, COMBINING_RUN, MAX_COMBINING);
		clean = collapse(clean, REPEATED, MAX_REPEAT);
		return clean;
	}

	/**
	 * Corta a corrida mantendo os primeiros {@code keep} caracteres — em vez de apagar o trecho
	 * (o que mudaria o texto de quem escreveu) ou deixar passar (o que trava a tela).
	 */
	private static String collapse(String text, Pattern pattern, int keep) {
		Matcher matcher = pattern.matcher(text);
		StringBuilder out = new StringBuilder();
		while (matcher.find()) {
			String run = matcher.group();
			matcher.appendReplacement(out, Matcher.quoteReplacement(run.substring(0, keep)));
		}
		matcher.appendTail(out);
		return out.toString();
	}
}
