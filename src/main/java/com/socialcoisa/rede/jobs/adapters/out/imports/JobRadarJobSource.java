package com.socialcoisa.rede.jobs.adapters.out.imports;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.shared.web.ApiException;
import com.socialcoisa.rede.jobs.application.port.out.JobImportSource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Fonte JobRadar, pela API OFICIAL `https://api.jobradar.com.br/v1/jobs`.
 *
 * A API existe e responde JSON, mas exige uma **API key** (401 "API key required") — que so o
 * JobRadar concede. Sem `JOBRADAR_API_KEY`, a fonte fica INDISPONIVEL (fail-safe): nao buscamos por
 * scraping da SPA nem pela API privada (`/api/*`, que exige request token e seria contornar
 * autorizacao).
 *
 * O nome/esquema do header de autenticacao **nao e publico** — por isso sao configuracao
 * (`JOBRADAR_AUTH_HEADER`/`JOBRADAR_AUTH_SCHEME`), a confirmar com a documentacao deles. Nao
 * inventamos contrato.
 */
@Component
public class JobRadarJobSource implements JobImportSource {

	private final String baseUrl;
	private final String apiKey;
	private final String authHeader;
	private final String authScheme;
	private final ObjectMapper objectMapper;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	public JobRadarJobSource(
			@Value("${rede.jobs.import.jobradar.base-url:https://api.jobradar.com.br}") String baseUrl,
			@Value("${rede.jobs.import.jobradar.api-key:}") String apiKey,
			@Value("${rede.jobs.import.jobradar.auth-header:Authorization}") String authHeader,
			@Value("${rede.jobs.import.jobradar.auth-scheme:Bearer}") String authScheme,
			ObjectMapper objectMapper) {
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		this.apiKey = apiKey;
		this.authHeader = authHeader;
		this.authScheme = authScheme;
		this.objectMapper = objectMapper;
	}

	@Override
	public String name() {
		return "JobRadar";
	}

	@Override
	public boolean available() {
		return StringUtils.hasText(apiKey);
	}

	@Override
	public List<ExternalJob> discover(int limit) {
		if (!available()) {
			throw new ApiException(503, "IMPORT_SOURCE_UNAVAILABLE",
				"A importacao do JobRadar ainda nao esta disponivel (falta a chave de API).");
		}
		int capped = Math.max(1, Math.min(limit, 100));
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/jobs?limit=" + capped))
				.timeout(Duration.ofSeconds(20))
				.header(authHeader, (StringUtils.hasText(authScheme) ? authScheme + " " : "") + apiKey)
				.header("Accept", "application/json")
				.GET()
				.build();
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() / 100 != 2) {
				// Falha tecnica nao vira importacao: erro claro, sem inventar dados.
				throw new ApiException(502, "IMPORT_SOURCE_ERROR",
					"Nao foi possivel consultar a fonte externa agora.");
			}
			return map(objectMapper.readTree(response.body()));
		}
		catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new ApiException(502, "IMPORT_SOURCE_ERROR", "Consulta a fonte externa interrompida.");
		}
		catch (ApiException apiException) {
			throw apiException;
		}
		catch (Exception failure) {
			throw new ApiException(502, "IMPORT_SOURCE_ERROR", "Falha ao consultar a fonte externa.");
		}
	}

	/** Mapeia o JSON da fonte para o modelo interno. Campo ausente fica nulo — nada e inventado. */
	private List<ExternalJob> map(JsonNode body) {
		JsonNode items = body.isArray() ? body : body.path("data");
		List<ExternalJob> jobs = new ArrayList<>();
		if (!items.isArray()) {
			return jobs;
		}
		for (JsonNode node : items) {
			jobs.add(new ExternalJob(
				text(node, "id"),
				text(node, "title"),
				text(node, "company"),
				text(node, "location"),
				text(node, "description"),
				text(node, "salary"),
				text(node, "employment_type"),
				text(node, "url"),
				text(node, "apply_url"),
				name(),
				null));
		}
		return jobs;
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.path(field);
		return value.isMissingNode() || value.isNull() ? null : value.asString();
	}
}
