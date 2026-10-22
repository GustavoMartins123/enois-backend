package com.socialcoisa.rede.jobs.application.port.out;

import java.util.List;

/**
 * Fonte externa de vagas. O modulo de vagas nao conhece fornecedor: cada plataforma implementa esta
 * porta (ex.: JobRadar) e e ligada por configuracao. Importacao so por API/feed oficial — nunca por
 * scraping de SPA nem pela API privada do fornecedor.
 */
public interface JobImportSource {

	/** Nome exibido da plataforma (ex.: "JobRadar"). */
	String name();

	/** Se a fonte esta realmente configurada (credencial presente). Sem isso, fica indisponivel. */
	boolean available();

	/** Busca vagas publicas na fonte. `limit` e um teto de itens por chamada. */
	List<ExternalJob> discover(int limit);

	/** Vaga normalizada para o modelo interno (nada inventado: campo ausente fica nulo). */
	record ExternalJob(String externalId, String title, String company, String location, String description,
			String salary, String employmentType, String url, String applyUrl, String platform,
			java.time.Instant publishedAt) {
	}
}
