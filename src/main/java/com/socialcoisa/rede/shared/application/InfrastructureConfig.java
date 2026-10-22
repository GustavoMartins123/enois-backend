package com.socialcoisa.rede.shared.application;

import java.time.Clock;
import java.util.UUID;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.socialcoisa.rede.feed.application.port.out.IdGenerator;
import com.socialcoisa.rede.feed.domain.PostId;
import com.socialcoisa.rede.identity.adapters.out.oauth.OAuthProperties;

@Configuration
public class InfrastructureConfig {

	@Bean
	Clock systemClock() {
		return Clock.systemUTC();
	}

	@Bean
	IdGenerator idGenerator() {
		return () -> new PostId("post_" + UUID.randomUUID().toString().replace("-", ""));
	}

	@Bean
	@ConfigurationProperties(prefix = "rede.oauth")
	OAuthProperties oAuthProperties() {
		return new OAuthProperties();
	}

	// Nao declaramos JavaMailSender aqui de proposito: a auto-configuracao do Spring Boot monta o bean
	// a partir de `spring.mail.*` (MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD). Um bean manual
	// com `new JavaMailSenderImpl()` faz o auto-config recuar (ConditionalOnMissingBean) e o envio cai
	// nos padroes do JavaMail (localhost:25), ignorando em silencio o SMTP configurado — nenhum e-mail
	// saia, nem o de redefinicao de senha.
}
