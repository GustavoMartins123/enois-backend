package com.socialcoisa.rede.moderation.application;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Executor do automod de spam: UMA thread daemon, fila própria.
 *
 * Uma thread só de propósito: a análise chama o LLM (até 30s) e não deve competir com as threads
 * de requisição. Fila própria mantém o atraso longe do "Publicar" de quem está usando a rede.
 */
@Configuration(proxyBeanMethods = false)
public class SpamAutomodConfig {

	@Bean(name = "spamAutomodExecutor", destroyMethod = "shutdown")
	ExecutorService spamAutomodExecutor() {
		return Executors.newSingleThreadExecutor(runnable -> {
			Thread thread = new Thread(runnable, "automod-spam");
			thread.setDaemon(true);
			return thread;
		});
	}
}
