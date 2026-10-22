package com.socialcoisa.rede.shared.observability;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Duas linhas por ciclo de vida (subida e descida) com o que identifica o artefato: serviço,
 * perfil, versão e porta. No CloudWatch elas são o marco para correlacionar um deploy com o
 * comportamento seguinte — sem isso, "quando essa versão subiu?" só se responde no ArgoCD.
 */
@Component
class StartupLogger {

	private static final Logger LOG = LoggerFactory.getLogger(StartupLogger.class);

	private final Environment environment;
	private final String version;

	StartupLogger(Environment environment, ObjectProvider<BuildProperties> buildProperties) {
		this.environment = environment;
		BuildProperties build = buildProperties.getIfAvailable();
		this.version = build != null ? build.getVersion() : "unknown";
	}

	@EventListener(ApplicationReadyEvent.class)
	void ready() {
		LOG.info("app_started service={} profile={} version={} port={}", service(),
			String.join(",", environment.getActiveProfiles()), version,
			environment.getProperty("local.server.port", environment.getProperty("server.port", "unknown")));
	}

	@PreDestroy
	void stopping() {
		LOG.info("app_stopping service={} profile={} version={}", service(),
			String.join(",", environment.getActiveProfiles()), version);
	}

	private String service() {
		return environment.getProperty("spring.application.name", "rede");
	}
}
