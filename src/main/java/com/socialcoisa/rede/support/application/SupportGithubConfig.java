package com.socialcoisa.rede.support.application;

import java.time.Clock;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.shared.security.SecretCipher;
import com.socialcoisa.rede.shared.web.ApiException;
import com.socialcoisa.rede.support.application.port.out.SupportSettingsRepository;

/**
 * Configuração do espelho do suporte no GitHub. O token, o repositório e o segredo do webhook
 * ficam CIFRADOS no banco (mesma chave do app) e podem ser trocados sem reiniciar o pod; as
 * variáveis de ambiente continuam valendo como fallback para uma instalação nova.
 *
 * Nada aqui devolve o segredo para fora: o painel só vê "configurado/não configurado".
 */
@Service
public class SupportGithubConfig {

	private static final Logger LOG = LoggerFactory.getLogger(SupportGithubConfig.class);

	static final String TOKEN = "github_token";
	static final String REPO = "github_repo";
	static final String WEBHOOK_SECRET = "github_webhook_secret";

	private final SupportSettingsRepository settings;
	private final SecretCipher cipher;
	private final Clock clock;
	private final String envToken;
	private final String envRepo;
	private final String envWebhookSecret;

	public SupportGithubConfig(SupportSettingsRepository settings, SecretCipher cipher, Clock clock,
			@Value("${rede.support.github.token:}") String envToken,
			@Value("${rede.support.github.repo:}") String envRepo,
			@Value("${rede.support.github.webhook-secret:}") String envSecret) {
		this.settings = settings;
		this.cipher = cipher;
		this.clock = clock;
		this.envToken = envToken == null ? "" : envToken.trim();
		this.envRepo = envRepo == null ? "" : envRepo.trim();
		this.envWebhookSecret = envSecret == null ? "" : envSecret.trim();
	}

	public String token() {
		return value(TOKEN, envToken);
	}

	public String repository() {
		return value(REPO, envRepo);
	}

	public String webhookSecret() {
		return value(WEBHOOK_SECRET, envWebhookSecret);
	}

	public boolean tokenConfigured() {
		return StringUtils.hasText(token());
	}

	public boolean webhookSecretConfigured() {
		return StringUtils.hasText(webhookSecret());
	}

	public boolean enabled() {
		return StringUtils.hasText(token()) && StringUtils.hasText(repository());
	}

	/**
	 * Troca a configuração. Campo nulo mantém o valor atual; string vazia remove. Exige a chave
	 * de cifra: sem ela os segredos não podem ser guardados com segurança.
	 */
	@Transactional
	public void update(String token, String repository, String webhookSecret) {
		if ((token != null && !token.isBlank()) || (webhookSecret != null && !webhookSecret.isBlank())) {
			cipher.requireEnabled();
		}
		if (repository != null && repository.isBlank()) {
			throw new ApiException(400, "INVALID_GITHUB_CONFIG", "Informe o repositório no formato dono/nome.");
		}
		if (repository != null && StringUtils.hasText(repository) && !repository.trim().matches("[\\w.-]+/[\\w.-]+")) {
			throw new ApiException(400, "INVALID_GITHUB_CONFIG", "Repositório deve ser dono/nome.");
		}
		apply(TOKEN, token);
		apply(REPO, repository);
		apply(WEBHOOK_SECRET, webhookSecret);
	}

	private void apply(String name, String value) {
		if (value == null) {
			return;
		}
		String clean = value.trim();
		if (clean.isEmpty()) {
			settings.delete(name);
			return;
		}
		settings.saveCipher(name, cipher.encrypt(clean), clock.instant());
	}

	private String value(String name, String fallback) {
		try {
			Optional<String> stored = settings.findCipher(name);
			if (stored.isPresent()) {
				String plain = cipher.decrypt(stored.get());
				if (StringUtils.hasText(plain)) {
					return plain.trim();
				}
			}
		}
		catch (RuntimeException failure) {
			// Chave ausente/trocada: não derruba o suporte, só desliga o espelho.
			LOG.warn("Configuração {} do GitHub indisponível: {}", name, failure.getClass().getSimpleName());
		}
		return fallback;
	}
}
