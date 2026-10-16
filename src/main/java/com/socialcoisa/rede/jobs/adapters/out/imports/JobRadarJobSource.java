package com.socialcoisa.rede.jobs.adapters.out.imports;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.shared.web.ApiException;
import com.socialcoisa.rede.jobs.application.port.out.JobImportSource;

/**
 * JobRadar como fonte de LISTAGEM + REDIRECIONAMENTO (sem credencial, sem scraping de JS).
 *
 * Usa o **sitemap publico** que o proprio JobRadar publica para indexadores
 * (`/sitemap-vagas.xml`, permitido no robots.txt — que so bloqueia URLs com `?`). Cada item vira uma
 * vaga externa com **titulo derivado do slug**, a **URL original** e o selo "Vaga externa". Nao
 * reproduzimos descricao/empresa/salario (ficam nulos) — a pessoa confere e se candidata no JobRadar.
 *
 * Nao usamos a API privada da SPA (`/api/*`, exige request token) nem a API key do `/v1`: isso seria
 * contornar controle de acesso. Se o JobRadar conceder acesso oficial, um adaptador por API entra
 * depois usando a mesma porta.
 */
@Component
public class JobRadarJobSource implements JobImportSource {

	private static final Pattern LOC = Pattern.compile("<loc>\\s*([^<\\s]+)\\s*</loc>");
	private static final Pattern SLUG = Pattern.compile("/vaga/(.+?)-(\\d+)$");

	private final String sitemapUrl;
	private final String userAgent;
	private final boolean enabled;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	public JobRadarJobSource(
			@Value("${rede.jobs.import.jobradar.sitemap-url:https://jobradar.com.br/sitemap-vagas.xml}") String sitemapUrl,
			@Value("${rede.jobs.import.jobradar.user-agent:EnóisBot/1.0 (+https://enois.ia.br/developers)}") String userAgent,
			@Value("${rede.jobs.import.jobradar.enabled:true}") boolean enabled) {
		this.sitemapUrl = sitemapUrl;
		this.userAgent = userAgent;
		this.enabled = enabled;
	}

	@Override
	public String name() {
		return "JobRadar";
	}

	/** Ligada por configuracao (e o sitemap e publico). Em teste, desligamos para nao ir a rede. */
	@Override
	public boolean available() {
		return enabled;
	}

	@Override
	public List<ExternalJob> discover(int limit) {
		int capped = Math.max(1, Math.min(limit, 500));
		String body;
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(sitemapUrl))
				.timeout(Duration.ofSeconds(20))
				.header("User-Agent", userAgent)
				.header("Accept", "application/xml")
				.GET()
				.build();
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() / 100 != 2) {
				throw new ApiException(502, "IMPORT_SOURCE_ERROR", "Nao foi possivel ler o sitemap do JobRadar.");
			}
			body = response.body();
		}
		catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new ApiException(502, "IMPORT_SOURCE_ERROR", "Consulta ao JobRadar interrompida.");
		}
		catch (ApiException apiException) {
			throw apiException;
		}
		catch (Exception failure) {
			throw new ApiException(502, "IMPORT_SOURCE_ERROR", "Falha ao consultar o JobRadar.");
		}
		return parse(body, capped);
	}

	/** Extrai as vagas do XML do sitemap: so titulo (do slug), URL e id. Nada e inventado. */
	List<ExternalJob> parse(String sitemapXml, int limit) {
		List<ExternalJob> jobs = new ArrayList<>();
		if (!StringUtils.hasText(sitemapXml)) {
			return jobs;
		}
		Matcher matcher = LOC.matcher(sitemapXml);
		while (matcher.find() && jobs.size() < limit) {
			String url = matcher.group(1);
			Matcher slug = SLUG.matcher(url);
			if (!slug.find()) {
				continue;
			}
			String title = humanize(slug.group(1));
			String externalId = slug.group(2);
			jobs.add(new ExternalJob(externalId, title, null, null, null, null, null, url, url, name(), null));
		}
		return jobs;
	}

	/** Conectivos que ficam minusculos no meio do titulo. */
	private static final java.util.Set<String> LOWERCASE = java.util.Set.of(
		"de", "da", "do", "das", "dos", "e", "em", "para", "com", "a", "o", "na", "no");

	/**
	 * "dev-java-senior-remoto" -> "Dev Java Senior Remoto". So o TITULO vem do slug; empresa, local e
	 * salario nao sao confiaveis no slug (ficam nulos) — nada e inventado.
	 */
	static String humanize(String slug) {
		String[] words = slug.replace('-', ' ').trim().replaceAll("\\s+", " ").split(" ");
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < words.length; i++) {
			String word = words[i];
			if (word.isEmpty()) {
				continue;
			}
			if (i == 0 || !LOWERCASE.contains(word)) {
				word = Character.toUpperCase(word.charAt(0)) + word.substring(1);
			}
			if (out.length() > 0) {
				out.append(' ');
			}
			out.append(word);
		}
		return out.length() == 0 ? slug : out.toString();
	}
}
