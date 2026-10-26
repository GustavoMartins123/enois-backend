package com.socialcoisa.rede.shared.web;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class CorsConfig {

	private static final String[] DEFAULT_ORIGINS = { "https://enois.ia.br", "http://localhost:5173" };

	@Value("${rede.cors.allowed-origins:}")
	private String allowedOrigins;

	/** Padrões confiáveis do app (aceita curinga, ex.: https://*.b7cloud.com.br). */
	@Value("${rede.origins.trusted:https://*.b7cloud.com.br}")
	private String trustedOrigins;

	@Bean
	WebMvcConfigurer corsConfigurer() {
		return new WebMvcConfigurer() {
			@Override
			public void addCorsMappings(CorsRegistry registry) {
				// allowedOriginPatterns e obrigatorio para aceitar "*" junto com
				// allowCredentials(true); allowedOrigins("*") lanca excecao em toda requisicao.
				// Sem configuracao explicita, restringe as origens conhecidas do app.
				Set<String> patterns = new LinkedHashSet<>();
				if (allowedOrigins.isBlank()) {
					patterns.addAll(Arrays.asList(DEFAULT_ORIGINS));
				}
				else {
					patterns.addAll(Arrays.stream(allowedOrigins.split(",")).map(String::trim)
						.filter(s -> !s.isEmpty()).toList());
				}
				// O gate de origem também confia nesses padrões; o CORS precisa liberá-los junto,
				// senão o navegador do frontend leva "Invalid CORS request" mesmo com origem válida.
				patterns.addAll(Arrays.stream(trustedOrigins.split(",")).map(String::trim)
					.filter(s -> !s.isEmpty()).toList());
				// Credentials (session cookie) are never offered to every origin.
				patterns.remove("*");
				registry.addMapping("/**")
					.allowedOriginPatterns(patterns.toArray(String[]::new))
					.allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
					.allowedHeaders("*")
					.exposedHeaders("*")
					.allowCredentials(true);
			}
		};
	}
}
