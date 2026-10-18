package com.socialcoisa.rede.shared.web;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;

/**
 * Diagnóstico PÚBLICO do pod que está servindo (nada sensível — decisão do plano de operação):
 * a versão do artefato e o estado do Flyway DESTA instância. É o que permite ler pelo ar qual
 * imagem está no cluster e se alguma migração ficou pendente/falhada, sem kubectl.
 */
@RestController
class VersionController {

	private final Instant startedAt = Instant.now();
	private final ObjectProvider<BuildProperties> build;
	private final ObjectProvider<Flyway> flyway;

	VersionController(ObjectProvider<BuildProperties> build, ObjectProvider<Flyway> flyway) {
		this.build = build;
		this.flyway = flyway;
	}

	@GetMapping("/version")
	Map<String, Object> version() {
		Map<String, Object> view = new LinkedHashMap<>();
		BuildProperties props = build.getIfAvailable();
		view.put("version", props != null ? props.getVersion() : "desconhecida");
		view.put("artefato", props != null && props.getTime() != null ? props.getTime().toString() : null);
		view.put("bootEm", startedAt.toString());
		view.put("uptimeSegundos", java.time.Duration.between(startedAt, Instant.now()).toSeconds());

		Flyway fw = flyway.getIfAvailable();
		if (fw != null) {
			Map<String, Object> estado = new LinkedHashMap<>();
			int applied = 0;
			int pending = 0;
			int failed = 0;
			String ultima = null;
			for (MigrationInfo info : fw.info().all()) {
				MigrationState state = info.getState();
				if (state == null) {
					continue;
				}
				switch (state) {
					case SUCCESS -> {
						applied++;
						ultima = info.getVersion() + " " + info.getDescription();
					}
					case PENDING -> pending++;
					case FAILED -> failed++;
					default -> {
						// IGNORED/MISSING etc não contam como saudável nem como problema de boot
					}
				}
			}
			estado.put("aplicadas", applied);
			estado.put("pendentes", pending);
			estado.put("falhadas", failed);
			estado.put("ultima", ultima);
			view.put("flyway", estado);
		}
		return view;
	}
}
