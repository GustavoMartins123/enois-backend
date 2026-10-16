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

	/** Limite do que e gravado: bate com a largura da coluna posts.page_html. */
	public static final int MAX_PAGE_HTML = 12_000;

	/** Corte do texto cru antes de parsear: evita gastar CPU com um paste gigante. */
	private static final int MAX_RAW_INPUT = 200_000;

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
		if (!StringUtils.hasText(raw)) {
			return null;
		}
		if (raw.length() > MAX_RAW_INPUT) {
			throw new ApiException(400, "PAGE_HTML_TOO_LONG", "HTML da pagina e grande demais.");
		}

		String css = sanitizeCss(collectCss(raw));
		// O <style> sai do markup antes da limpeza e volta sanitizado, no formato que o front espera.
		String withoutStyles = STYLE_ELEMENT.matcher(raw).replaceAll("");
		Document clean = new Cleaner(PAGE_ALLOWLIST).clean(Jsoup.parse(withoutStyles));
		clean.outputSettings().prettyPrint(false);

		String markup = clean.body().html().trim();
		String result = (css.isEmpty() ? "" : "<style>" + css + "</style>") + sanitizeStyleAttributes(markup);
		if (result.length() > MAX_PAGE_HTML) {
			throw new ApiException(400, "PAGE_HTML_TOO_LONG",
				"HTML da pagina deve ter no maximo " + MAX_PAGE_HTML + " caracteres.");
		}
		return result.isEmpty() ? null : result;
	}

	/** HTML obrigatorio (posts do tipo PAGE): mesma limpeza, mas recusa quando nao sobra nada. */
	public static String requirePageHtml(String raw) {
		String sanitized = sanitizePageHtml(raw);
		if (sanitized == null) {
			throw new ApiException(400, "INVALID_PAGE_HTML", "Pagina precisa de HTML valido.");
		}
		return sanitized;
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
			addTags("a", "abbr", "article", "aside", "b", "blockquote", "br", "caption", "cite", "code", "col",
				"colgroup", "dd", "del", "details", "dfn", "div", "dl", "dt", "em", "figcaption", "figure",
				"footer", "h1", "h2", "h3", "h4", "h5", "h6", "header", "hr", "i", "img", "ins", "kbd", "li",
				"main", "mark", "ol", "p", "pre", "q", "s", "samp", "section", "small", "span", "strong", "sub",
				"summary", "sup", "table", "tbody", "td", "tfoot", "th", "thead", "time", "tr", "u", "ul", "wbr");
			addAttributes(":all", "class", "style", "title", "dir", "lang", "align", "width", "height");
			addAttributes("a", "href", "target", "rel");
			addAttributes("img", "src", "alt", "loading");
			addAttributes("ol", "start", "type", "reversed");
			addAttributes("li", "value");
			addAttributes("table", "border", "cellpadding", "cellspacing");
			addAttributes("td", "colspan", "rowspan", "headers");
			addAttributes("th", "colspan", "rowspan", "scope", "headers");
			addProtocols("a", "href", "http", "https", "mailto");
			addProtocols("img", "src", "http", "https", "data");
		}
	}

	private static Safelist buildAllowlist() {
		return new PageSafelist();
	}
}
