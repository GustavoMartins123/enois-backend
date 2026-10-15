package com.socialcoisa.rede.shared.persistence;

import java.util.Arrays;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Recuperacao de migracao FAILED antes de migrar.
 *
 * O DDL do MariaDB nao e transacional: quando uma migracao quebra no meio (foi o caso da V17, que
 * tentava criar uma FK entre collations diferentes), o Flyway deixa a versao marcada como FAILED na
 * flyway_schema_history e passa a recusar QUALQUER migracao seguinte nesse banco. O pod nao sobe, o
 * rollout trava e o cluster continua servindo a versao antiga em silencio.
 *
 * Aqui o boot limpa esse marcador com `repair()` — que so remove entradas FAILED e realinha
 * checksums — e tenta de novo. Sem isso, tirar o backend do ar exigiria alguem com acesso ao banco
 * rodando `flyway repair` na mao.
 */
@Configuration(proxyBeanMethods = false)
class FlywayMigrationConfig {

	private static final Logger LOG = LoggerFactory.getLogger(FlywayMigrationConfig.class);

	@Bean
	FlywayMigrationStrategy repairFailedBeforeMigrate() {
		return FlywayMigrationConfig::migrateWithRepair;
	}

	static void migrateWithRepair(Flyway flyway) {
		List<String> failed = Arrays.stream(flyway.info().all())
			.filter(info -> info.getState() != null && info.getState().isFailed())
			.map(FlywayMigrationConfig::describe)
			.toList();
		if (!failed.isEmpty()) {
			LOG.warn("Flyway encontrou migracao(oes) FAILED {}; limpando o marcador (repair) antes de migrar.",
				failed);
			flyway.repair();
		}
		flyway.migrate();
	}

	private static String describe(MigrationInfo info) {
		return info.getVersion() + " - " + info.getDescription();
	}
}
