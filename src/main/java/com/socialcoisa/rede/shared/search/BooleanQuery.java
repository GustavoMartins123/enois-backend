package com.socialcoisa.rede.shared.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pesquisa booleana para filtros de lista (hoje usada nas vagas).
 *
 * Sintaxe aceita:
 * <ul>
 *   <li>termos soltos — {@code java spring} (AND implicito);</li>
 *   <li>{@code AND} / {@code E}, {@code OR} / {@code OU}, {@code NOT} / {@code NAO} / {@code NÃO};</li>
 *   <li>{@code -termo} para excluir e {@code +termo} para exigir (o {@code +} e o AND implicito);</li>
 *   <li>{@code "frase exata"};</li>
 *   <li>parenteses para agrupar: {@code (java OR kotlin) -estagio}.</li>
 * </ul>
 *
 * A ordem de avaliacao segue o padrao: {@code NOT} &gt; {@code AND} &gt; {@code OR}.
 *
 * O parser nunca falha: entrada desbalanceada (aspas ou parenteses sem par) e interpretada como
 * texto literal, porque quem digita uma busca nao deveria receber erro de sintaxe. O que ele faz e
 * limitar tamanho e profundidade — uma expressao absurda viraria uma clausula WHERE enorme.
 *
 * A normalizacao aqui e so de caixa e espacos. Acento e tratado pela collation do banco
 * ({@code utf8mb4_uca1400_ai_ci}, ai = accent-insensitive), entao "analise" acha "análise".
 */
public final class BooleanQuery {

	/** Teto de termos: acima disso a consulta vira abuso de recurso, nao busca. */
	private static final int MAX_TOKENS = 64;

	/** Teto de aninhamento de parenteses. */
	private static final int MAX_DEPTH = 12;

	public sealed interface Node permits MatchAll, Term, Phrase, And, Or, Not {
	}

	/** Nenhum filtro: casa com tudo. */
	public record MatchAll() implements Node {
	}

	public record Term(String text) implements Node {
	}

	public record Phrase(String text) implements Node {
	}

	public record And(List<Node> children) implements Node {
	}

	public record Or(List<Node> children) implements Node {
	}

	public record Not(Node child) implements Node {
	}

	private final Node root;

	private BooleanQuery(Node root) {
		this.root = root;
	}

	public Node root() {
		return root;
	}

	public boolean isEmpty() {
		return root instanceof MatchAll;
	}

	public static BooleanQuery parse(String raw) {
		if (raw == null || raw.isBlank()) {
			return new BooleanQuery(new MatchAll());
		}
		List<Token> tokens = tokenize(raw);
		if (tokens.isEmpty()) {
			return new BooleanQuery(new MatchAll());
		}
		if (tokens.size() > MAX_TOKENS) {
			throw new IllegalArgumentException(
				"Busca muito longa: use no maximo " + MAX_TOKENS + " termos.");
		}
		// Fecha-parentese sobrando no comeco e ruido de digitacao (") java"): o ")" so termina um grupo
		// aberto, entao no nivel de fora ele e ignorado em vez de encerrar a busca.
		int start = 0;
		while (start < tokens.size() && tokens.get(start).kind() == Kind.CLOSE) {
			start++;
		}
		Parser parser = new Parser(tokens.subList(start, tokens.size()));
		Node node = parser.parseExpression(0);
		return new BooleanQuery(node == null ? new MatchAll() : node);
	}

	// ---------------------------------------------------------------- tokens

	private enum Kind {
		AND, OR, NOT, TERM, PHRASE, OPEN, CLOSE
	}

	private record Token(Kind kind, String text) {
	}

	private static List<Token> tokenize(String raw) {
		List<Token> tokens = new ArrayList<>();
		int index = 0;
		int length = raw.length();

		while (index < length) {
			char current = raw.charAt(index);

			if (Character.isWhitespace(current)) {
				index++;
				continue;
			}
			if (current == '(') {
				tokens.add(new Token(Kind.OPEN, "("));
				index++;
				continue;
			}
			if (current == ')') {
				tokens.add(new Token(Kind.CLOSE, ")"));
				index++;
				continue;
			}
			if (current == '"') {
				// Frase: vai ate a proxima aspa. Sem fechar, vale o resto da linha.
				int end = raw.indexOf('"', index + 1);
				String phrase = end == -1 ? raw.substring(index + 1) : raw.substring(index + 1, end);
				if (!phrase.isBlank()) {
					tokens.add(new Token(Kind.PHRASE, normalize(phrase)));
				}
				index = end == -1 ? length : end + 1;
				continue;
			}
			if (current == '-' || current == '+') {
				// "-termo" exclui; "+termo" apenas reforca o AND que ja e implicito.
				if (index + 1 < length && !isBoundary(raw.charAt(index + 1))) {
					if (current == '-') {
						tokens.add(new Token(Kind.NOT, "-"));
					}
					index++;
					continue;
				}
				// Sinal solto (fim da busca, antes de espaco ou de parentese) e ruido de digitacao.
				// Hifen dentro da palavra ("back-end") nao passa por aqui: e lido junto com o termo.
				index++;
				continue;
			}

			int start = index;
			while (index < length && !isBoundary(raw.charAt(index))) {
				index++;
			}
			String word = raw.substring(start, index);
			tokens.add(new Token(classify(word), normalize(word)));
		}
		return tokens;
	}

	private static boolean isBoundary(char value) {
		return Character.isWhitespace(value) || value == '(' || value == ')' || value == '"';
	}

	/** Operadores valem em ingles e em portugues, sempre como palavra inteira. */
	private static Kind classify(String word) {
		String upper = word.toUpperCase(Locale.ROOT);
		return switch (upper) {
			case "AND", "E" -> Kind.AND;
			case "OR", "OU" -> Kind.OR;
			case "NOT", "NAO", "NÃO" -> Kind.NOT;
			default -> Kind.TERM;
		};
	}

	/** Caixa baixa e espacos colapsados: e a forma comparada com o texto das colunas. */
	public static String normalize(String value) {
		return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
	}

	// ---------------------------------------------------------------- parser

	private static final class Parser {

		private final List<Token> tokens;
		private int position;

		Parser(List<Token> tokens) {
			this.tokens = tokens;
		}

		/** OR tem a menor precedencia: junta os blocos de AND. */
		Node parseExpression(int depth) {
			if (depth > MAX_DEPTH) {
				throw new IllegalArgumentException("Busca com parenteses aninhados demais.");
			}
			Node left = parseAnd(depth);
			List<Node> branches = new ArrayList<>();
			if (left != null) {
				branches.add(left);
			}
			while (peekIs(Kind.OR)) {
				position++;
				Node right = parseAnd(depth);
				if (right != null) {
					branches.add(right);
				}
			}
			if (branches.isEmpty()) {
				return null;
			}
			return branches.size() == 1 ? branches.get(0) : new Or(List.copyOf(branches));
		}

		/** Termos vizinhos valem como AND (padrao de busca), inclusive depois de NOT. */
		Node parseAnd(int depth) {
			List<Node> children = new ArrayList<>();
			while (true) {
				if (peekIs(Kind.AND)) {
					position++;
					continue;
				}
				if (!startsPrimary()) {
					break;
				}
				Node node = parseUnary(depth);
				if (node != null) {
					children.add(node);
				}
			}
			if (children.isEmpty()) {
				return null;
			}
			return children.size() == 1 ? children.get(0) : new And(List.copyOf(children));
		}

		Node parseUnary(int depth) {
			if (peekIs(Kind.NOT)) {
				position++;
				Node child = parseUnary(depth);
				return child == null ? null : new Not(child);
			}
			return parsePrimary(depth);
		}

		Node parsePrimary(int depth) {
			Token token = peek();
			if (token == null) {
				return null;
			}
			switch (token.kind()) {
				case OPEN -> {
					position++;
					Node inner = parseExpression(depth + 1);
					// Parentese sem fechar: seguimos como se tivesse fechado no fim.
					if (peekIs(Kind.CLOSE)) {
						position++;
					}
					return inner;
				}
				case PHRASE -> {
					position++;
					return new Phrase(token.text());
				}
				case TERM -> {
					position++;
					return new Term(token.text());
				}
				// ")" solto ou operador no lugar de um termo: ignora e segue.
				default -> {
					position++;
					return null;
				}
			}
		}

		private boolean startsPrimary() {
			Token token = peek();
			if (token == null) {
				return false;
			}
			return token.kind() == Kind.OPEN || token.kind() == Kind.PHRASE || token.kind() == Kind.TERM
				|| token.kind() == Kind.NOT;
		}

		private Token peek() {
			return position < tokens.size() ? tokens.get(position) : null;
		}

		private boolean peekIs(Kind kind) {
			Token token = peek();
			return token != null && token.kind() == kind;
		}
	}
}