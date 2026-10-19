package com.socialcoisa.rede.blog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import com.socialcoisa.rede.blog.application.port.out.BlogSubscriptionRepository;
import com.socialcoisa.rede.identity.application.EmailService;
import com.socialcoisa.rede.profile.application.port.out.UserProfileRepository;
import com.socialcoisa.rede.shared.security.SecretCipher;

/** O token de descadastro cifra (blog, assinante) e volta; token estranho nunca derruba inscrição. */
class BlogUnsubscribeTokenTests {

	private final BlogSubscriptionRepository subscriptions = mock(BlogSubscriptionRepository.class);

	private BlogNewsletterService service() {
		String chave = Base64.getEncoder().encodeToString(new byte[32]);
		var templateEngine = new TemplateEngine();
		templateEngine.setTemplateResolver(new StringTemplateResolver());
		return new BlogNewsletterService(subscriptions, mock(UserProfileRepository.class),
			mock(EmailService.class), new SecretCipher(chave), templateEngine, Runnable::run, "https://enois.ia.br");
	}

	@Test
	void roundTripDoToken() {
		when(subscriptions.delete("blog_1", "user_2")).thenReturn(1);
		BlogNewsletterService service = service();

		String token = service.unsubscribeToken("blog_1", "user_2");

		assertThat(service.unsubscribeByToken(token)).isTrue();
	}

	@Test
	void tokenInvalidoNaoMexeEmNada() {
		BlogNewsletterService service = service();
		assertThat(service.unsubscribeByToken("nao-e-token")).isFalse();
		assertThat(service.unsubscribeByToken(null)).isFalse();
		assertThat(service.unsubscribeByToken("")).isFalse();
	}

	@Test
	void tokenDeOutraPessoaNaoRemoveNada() {
		when(subscriptions.delete(anyString(), anyString())).thenReturn(0);
		BlogNewsletterService service = service();
		assertThat(service.unsubscribeByToken(service.unsubscribeToken("blog_9", "user_9"))).isFalse();
	}
}
