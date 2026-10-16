package com.socialcoisa.rede.shared.html;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Limpeza do HTML das "paginas" publicadas pelos usuarios.
 *
 * O render acontece num iframe com sandbox e sem scripts (nem o nosso front o executa), entao o
 * risco ja e baixo por construcao. Ainda assim o markup e limpo aqui, na entrada, por tres
 * motivos: o que fica guardado nao depende de quem renderiza depois (app, e-mail, crawler),
 * o conteudo cortado e o mesmo para todos os clientes, e um sanitizador conhecido e mais facil
 * de auditar do que confiar so no sandbox.
 *
 * Estrategia: allowlist de tags/atributos (jsoup Safelist, que tambem remove atributos de evento
 * e protocolos perigosos como javascript:) + limpeza de CSS (sem @import, expression(), url()
 * para fora de https:/data:), porque o style inline e o unico jeito do usuario dar aparencia a
 * pagina e sem ele o recurso perde sentido.
 */
public final class SafeHtml {

	/**
	 * Teto de HTML renderizado por pagina, em caracteres, para TODO MUNDO: nao ha excecao por
	 * reputacao, tipo de conta ou idade da conta, e o mesmo numero vale na publicacao e na edicao.
	 * O front usa o mesmo valor no campo de escrita (`PAGE_HTML_MAX_LENGTH`), para ninguem digitar
	 * algo que o servidor vai recusar depois.
	 */
	public static final int MAX_PAGE_HTML = 100_000;

	/**
	 * Teto do HTML da bio do perfil. E o mesmo para todo mundo, como o da pagina; e menor porque o
	 * perfil e aberto em muito mais telas (lista de conexoes, busca, sugestoes) do que um post.
	 * O front espelha esse numero em `PROFILE_HTML_MAX_LENGTH`.
	 */
	public static final int MAX_PROFILE_HTML = 20_000;

	/**
	 * Corte do texto cru antes de parsear: evita gastar CPU com um paste gigante. Fica acima do
	 * teto porque a limpeza costuma encolher o markup (scripts fora, tags nao permitidas fora).
	 */
	private static final int MAX_RAW_INPUT = 400_000;

	private static final Pattern STYLE_IMPORT = Pattern.compile("(?is)@import\\b[^;]*;?");
	private static final Pattern STYLE_DANGEROUS = Pattern
		.compile("(?is)(expression\\s*\\(|javascript\\s*:|vbscript\\s*:|-moz-binding\\s*:|behavior\\s*:[^;]*;?)");
	private static final Pattern STYLE_URL = Pattern.compile("(?is)url\\s*\\(\\s*(['\"]?)([^'\")]*)\\1\\s*\\)");
	private static final Pattern STYLE_ATTRIBUTE = Pattern.compile("(?is)\\sstyle\\s*=\\s*(\"([^\"]*)\"|'([^']*)')");
	private static final Pattern STYLE_ELEMENT = Pattern.compile("(?is)<style\\b[^>]*>(.*?)</style>");

	private static final Safelist PAGE_ALLOWLIST = buildAllowlist();

	private SafeHtml() {
	}

	/**
	 * Sanitiza o HTML de uma pagina. Devolve {@code null} quando nao sobra nada util.
	 * O conteudo do {@code <style>} do documento colado e preservado e reanexado no inicio.
	 */
	public static String sanitizePageHtml(String raw) {
		return sanitize(raw, MAX_PAGE_HTML, "PAGE_HTML_TOO_LONG", "HTML da pagina");
	}

	/** HTML obrigatorio (posts do tipo PAGE): mesma limpeza, mas recusa quando nao sobra nada. */
	public static String requirePageHtml(String raw) {
		String sanitized = sanitizePageHtml(raw);
		if (sanitized == null) {
			throw new ApiException(400, "INVALID_PAGE_HTML", "Pagina precisa de HTML valido.");
		}
		return sanitized;
	}

	/**
	 * HTML da bio do perfil: mesma limpeza e mesma renderizacao isolada do post de pagina, com teto
	 * proprio. Devolve {@code null} quando nao sobra nada util (o perfil volta a mostrar a bio de
	 * texto, que continua existindo ao lado).
	 */
	public static String sanitizeProfileHtml(String raw) {
		return sanitize(raw, MAX_PROFILE_HTML, "PROFILE_HTML_TOO_LONG", "HTML do perfil");
	}

	public static String requireProfileHtml(String raw) {
		String sanitized = sanitizeProfileHtml(raw);
		if (sanitized == null) {
			throw new ApiException(400, "INVALID_PROFILE_HTML", "Bio em HTML precisa de conteudo valido.");
		}
		return sanitized;
	}

	/**
	 * Limpeza em si, compartilhada entre pagina e bio: allowlist de tags/atributos, protocolos
	 * restritos, CSS sem import/expression/url remota, e teto aplicado sobre o RESULTADO (que e o
	 * valor guardado e renderizado).
	 */
	private static String sanitize(String raw, int maxLength, String tooLongCode, String label) {
		if (!StringUtils.hasText(raw)) {
			return null;
		}
		if (raw.length() > MAX_RAW_INPUT) {
			throw new ApiException(400, tooLongCode, label + " e grande demais.");
		}

		String css = sanitizeCss(collectCss(raw));
		// O <style> sai do markup antes da limpeza e volta sanitizado, no formato que o front espera.
		String withoutStyles = STYLE_ELEMENT.matcher(raw).replaceAll("");
		Document clean = new Cleaner(PAGE_ALLOWLIST).clean(Jsoup.parse(withoutStyles));
		clean.outputSettings().prettyPrint(false);

		String markup = clean.body().html().trim();
		String result = (css.isEmpty() ? "" : "<style>" + css + "</style>") + sanitizeStyleAttributes(markup);
		if (result.length() > maxLength) {
			throw new ApiException(400, tooLongCode,
				label + " deve ter no maximo " + maxLength + " caracteres.");
		}
		return result.isEmpty() ? null : result;
	}

	/** Junta o CSS de todos os blocos {@code <style>} do documento colado (head e body). */
	private static String collectCss(String raw) {
		StringBuilder css = new StringBuilder();
		Matcher matcher = STYLE_ELEMENT.matcher(raw);
		while (matcher.find()) {
			css.append(matcher.group(1)).append('\n');
		}
		return css.toString();
	}

	/** Remove do CSS o que pode sair da caixa: importacao remota, expressao e url() nao-https. */
	private static String sanitizeCss(String css) {
		if (!StringUtils.hasText(css)) {
			return "";
		}
		String out = css.replace("<", "");
		out = STYLE_IMPORT.matcher(out).replaceAll("");
		out = STYLE_DANGEROUS.matcher(out).replaceAll("");
		out = STYLE_URL.matcher(out).replaceAll(match -> {
			String target = match.group(2).trim().toLowerCase(Locale.ROOT);
			// Imagem em https e data: sao o que a pagina legitimamente carrega; o resto e rastro.
			return target.startsWith("https:") || target.startsWith("data:") ? match.group(0) : "";
		});
		return out.trim();
	}

	private static String sanitizeStyleAttributes(String markup) {
		return STYLE_ATTRIBUTE.matcher(markup).replaceAll(match -> {
			String value = match.group(2) != null ? match.group(2) : match.group(3);
			String cleaned = sanitizeCss(value).replace("\"", "'").replace("&quot;", "'");
			return cleaned.isEmpty() ? "" : " style=\"" + cleaned + "\"";
		});
	}

	/**
	 * Allowlist de markup. {@code addProtocols} e protegido no jsoup, entao a lista e montada numa
	 * subclasse — e ela nao e opcional: sem regra de protocolo para {@code a[href]}/{@code img[src]},
	 * o jsoup aceita qualquer esquema, inclusive {@code javascript:}.
	 */
	private static final class PageSafelist extends Safelist {

		PageSafelist() {
			// `video`, `audio` e `picture` entram porque sao a forma de midia que roda no navegador
			// SEM script: o player e o proprio navegador (controles nativos), entao a promessa de
			// "nenhum script executa" continua valendo com midia embutida.
			addTags("a", "abbr", "article", "aside", "audio", "b", "blockquote", "br", "caption", "cite", "code",
				"col", "colgroup", "dd", "del", "details", "dfn", "div", "dl", "dt", "em", "figcaption", "figure",
				"footer", "h1", "h2", "h3", "h4", "h5", "h6", "header", "hr", "i", "img", "ins", "kbd", "li",
				"main", "mark", "ol", "p", "picture", "pre", "q", "s", "samp", "section", "small", "source",
				"span", "strong", "sub", "summary", "sup", "table", "tbody", "td", "tfoot", "th", "thead", "time",
				"tr", "track", "u", "ul", "video", "wbr");
			addAttributes(":all", "class", "style", "title", "dir", "lang", "align", "width", "height");
			addAttributes("a", "href", "target", "rel");
			addAttributes("img", "src", "alt", "loading");
			addAttributes("video", "src", "poster", "controls", "preload", "loop", "muted", "playsinline");
			addAttributes("audio", "src", "controls", "preload", "loop", "muted");
			addAttributes("source", "src", "type", "media");
			addAttributes("track", "src", "kind", "srclang", "label", "default");
			addAttributes("ol", "start", "type", "reversed");
			addAttributes("li", "value");
			addAttributes("table", "border", "cellpadding", "cellspacing");
			addAttributes("td", "colspan", "rowspan", "headers");
			addAttributes("th", "colspan", "rowspan", "scope", "headers");
			addProtocols("a", "href", "http", "https", "mailto");
			// Midia e documento so por https (ou embutido em data:). Endereco http nao carregaria de
			// qualquer forma numa pagina https — o navegador bloqueia como conteudo misto — e deixar o
			// atributo passar so esconderia o problema. `<iframe>`/`<embed>`/`<object>` ficam de fora:
			// o visualizador de PDF do Chrome nao roda dentro do sandbox (conferido no navegador), e
			// entao documento fica como link, que abre no visualizador nativo em outra aba.
			addProtocols("img", "src", "https", "data");
			addProtocols("video", "src", "https", "data");
			addProtocols("video", "poster", "https", "data");
			addProtocols("audio", "src", "https", "data");
			addProtocols("source", "src", "https", "data");
			addProtocols("track", "src", "https");
		}
	}

	private static Safelist buildAllowlist() {
		return new PageSafelist();
	}
}
