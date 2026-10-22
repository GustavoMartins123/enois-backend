package com.socialcoisa.rede.linkedin.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.linkedin.application.port.out.LinkedInConnectionRepository;
import com.socialcoisa.rede.linkedin.application.port.out.LinkedInShareClient;
import com.socialcoisa.rede.linkedin.application.port.out.LinkedInShareClient.ShareOutcome;
import com.socialcoisa.rede.linkedin.domain.LinkedInConnection;
import com.socialcoisa.rede.shared.security.SecretCipher;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Publicacao das publicacoes do Enois no LinkedIn (produto "Share on LinkedIn").
 *
 * Decisoes:
 * - o token de acesso e CIFRADO (SecretCipher); sem REDE_SECURITY_ENCRYPTION_KEY a integracao se
 *   declara indisponivel e nada e gravado — token em claro nunca;
 * - a preferencia (postSync) e por pessoa e editavel depois; publicar o post nao pode falhar por
 *   causa do LinkedIn (o envio e melhor esforco, fora da transacao da publicacao);
 * - so viajam o texto do post e o link publico dele; nada de conteudo de grupo (grupos nem
 *   passam pelo publish do feed principal).
 */
@Service
public class LinkedInIntegrationService {

	private static final Logger LOG = LoggerFactory.getLogger(LinkedInIntegrationService.class);

	/** O shareCommentary do LinkedIn aceita ate 3.000 caracteres. */
	private static final int MAX_COMMENTARY = 3_000;
	static final String REASON_NOT_CONNECTED = "NOT_CONNECTED";
	static final String REASON_SERVER_KEY_MISSING = "SERVER_KEY_MISSING";
	static final String REASON_TOKEN_EXPIRED = "TOKEN_EXPIRED";

	private final LinkedInConnectionRepository connections;
	private final LinkedInShareClient shareClient;
	private final SecretCipher cipher;
	private final Clock clock;
	private final String baseUrl;

	public LinkedInIntegrationService(LinkedInConnectionRepository connections, LinkedInShareClient shareClient,
			SecretCipher cipher, Clock clock,
			@Value("${rede.frontend.base-url:https://enois.ia.br}") String baseUrl) {
		this.connections = connections;
		this.shareClient = shareClient;
		this.cipher = cipher;
		this.clock = clock;
		this.baseUrl = baseUrl;
	}

	public record Status(boolean connected, boolean postSync, boolean canPost, Instant tokenExpiresAt,
			String reason, String lastShareStatus, Instant lastShareAt) {
	}

	/**
	 * Guarda (ou renova) o token recebido no callback do OAuth. Chamado tanto no vinculo quanto no
	 * login com LinkedIn; sem a chave de criptografia, nada e guardado.
	 */
	@Transactional
	public void remember(String userId, String memberSub, String accessToken, Instant tokenExpiresAt) {
		if (!StringUtils.hasText(userId) || !StringUtils.hasText(memberSub)
				|| !StringUtils.hasText(accessToken)) {
			return;
		}
		if (!cipher.isEnabled()) {
			LOG.info("Token do LinkedIn nao guardado: REDE_SECURITY_ENCRYPTION_KEY nao configurada.");
			return;
		}
		Instant now = clock.instant();
		LinkedInConnection existing = connections.findByUserId(userId).orElse(null);
		Instant createdAt = existing != null ? existing.createdAt() : now;
		boolean postSync = existing != null && existing.postSync();
		Instant expiresAt = tokenExpiresAt != null ? tokenExpiresAt : now.plus(Duration.ofDays(1));
		connections.save(new LinkedInConnection(userId, memberSub, cipher.encrypt(accessToken), expiresAt,
			postSync, createdAt, now, null, null));
	}

	@Transactional(readOnly = true)
	public Status status(String userId) {
		LinkedInConnection connection = connections.findByUserId(userId).orElse(null);
		if (connection == null) {
			return new Status(false, false, false, null, REASON_NOT_CONNECTED, null, null);
		}
		boolean canPost = cipher.isEnabled() && connection.tokenValid(clock.instant());
		String reason = canPost ? null
			: (!cipher.isEnabled() ? REASON_SERVER_KEY_MISSING : REASON_TOKEN_EXPIRED);
		return new Status(true, connection.postSync(), canPost, connection.tokenExpiresAt(), reason,
			connection.lastShareStatus(), connection.lastShareAt());
	}

	@Transactional
	public Status setPostSync(String userId, boolean enabled) {
		LinkedInConnection connection = connections.findByUserId(userId)
			.orElseThrow(() -> new ApiException(409, "LINKEDIN_NOT_CONNECTED",
				"Vincule o LinkedIn antes de ativar a sincronizacao."));
		if (enabled && (!cipher.isEnabled() || !connection.tokenValid(clock.instant()))) {
			throw new ApiException(409, "LINKEDIN_NOT_READY",
				"Reconecte o LinkedIn para autorizar a publicacao em seu nome.");
		}
		connections.save(connection.withPostSync(enabled, clock.instant()));
		return status(userId);
	}

	/** Ao desvincular o LinkedIn, a conexao de publicacao morre junto (o token deixa de existir). */
	@Transactional
	public void remove(String userId) {
		connections.deleteByUserId(userId);
	}

	/**
	 * Melhor esforco: chamado pelo handler do outbox depois da publicacao. Silencioso de proposito
	 * — uma falha do LinkedIn nunca pode derrubar (nem atrasar) a publicacao no Enois.
	 */
	public void sharePublishedPost(String authorId, String postId, String content, String title) {
		LinkedInConnection connection = connections.findByUserId(authorId).orElse(null);
		if (connection == null || !connection.postSync() || !cipher.isEnabled()
				|| !connection.tokenValid(clock.instant())) {
			return;
		}
		String accessToken;
		try {
			accessToken = cipher.decrypt(connection.accessTokenEnc());
		}
		catch (RuntimeException exception) {
			LOG.warn("Nao foi possivel decifrar o token do LinkedIn de {}: {}", authorId, exception.getMessage());
			return;
		}
		String url = baseUrl.replaceAll("/+$", "") + "/post/" + postId;
		ShareOutcome outcome = shareClient.share(accessToken, connection.memberSub(), commentary(content, url), url,
			title);
		// O resultado fica na linha da pessoa: o painel mostra o que aconteceu sem depender do
		// log do pod (a falha em si continua silenciosa — nunca derruba a publicacao).
		connections.save(connection.withShareOutcome(outcome.accepted() ? "OK" : outcome.detail(), clock.instant()));
	}

	/** Texto do share: o conteudo do post e o link publico, cabendo no limite do LinkedIn. */
	static String commentary(String content, String url) {
		String base = content == null ? "" : content.trim();
		int room = MAX_COMMENTARY - url.length() - 2;
		if (room < 0) {
			room = 0;
		}
		if (base.length() > room) {
			base = room == 0 ? "" : base.substring(0, Math.max(room - 1, 0)).trim() + "…";
		}
		return base.isEmpty() ? url : base + "\n\n" + url;
	}
}
