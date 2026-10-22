package com.socialcoisa.rede.push.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.push.application.port.out.PushSender;
import com.socialcoisa.rede.push.application.port.out.PushSubscriptionRepository;
import com.socialcoisa.rede.push.domain.PushSubscription;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Registro das inscricoes de Web Push do usuario (uma por dispositivo/navegador).
 */
@Service
public class PushService {

	private final PushSubscriptionRepository repository;
	private final PushSender sender;
	private final Clock clock;

	public PushService(PushSubscriptionRepository repository, PushSender sender, Clock clock) {
		this.repository = repository;
		this.sender = sender;
		this.clock = clock;
	}

	public boolean isEnabled() {
		return sender.isEnabled();
	}

	/** Chave publica VAPID para o app se inscrever (nula se o push estiver desligado). */
	public String publicKey() {
		return sender.publicKey();
	}

	@Transactional
	public PushSubscription register(String userId, String endpoint, String p256dh, String auth) {
		if (!StringUtils.hasText(endpoint) || !endpoint.startsWith("https://")) {
			throw new ApiException(400, "INVALID_PUSH_SUBSCRIPTION", "Endpoint de push invalido.");
		}
		if (!StringUtils.hasText(p256dh) || !StringUtils.hasText(auth)) {
			throw new ApiException(400, "INVALID_PUSH_SUBSCRIPTION", "Chaves do dispositivo obrigatorias.");
		}
		String trimmedEndpoint = endpoint.trim();
		PushSubscription subscription = repository.findByEndpoint(trimmedEndpoint)
			.map(existing -> {
				// Mesmo dispositivo voltando (ou trocando de conta): atualiza o dono e as chaves.
				return PushSubscription.restore(existing.id(), userId, trimmedEndpoint, p256dh.trim(), auth.trim(),
					existing.createdAt(), clock.instant());
			})
			.orElseGet(() -> PushSubscription.create("push_" + UUID.randomUUID().toString().replace("-", ""), userId,
				trimmedEndpoint, p256dh.trim(), auth.trim(), clock.instant()));
		return repository.save(subscription);
	}

	@Transactional
	public void unregister(String userId, String endpoint) {
		if (!StringUtils.hasText(endpoint)) {
			throw new ApiException(400, "INVALID_PUSH_SUBSCRIPTION", "Endpoint de push obrigatorio.");
		}
		repository.deleteByEndpointAndUserId(endpoint.trim(), userId);
	}

	public List<PushSubscription> listByUser(String userId) {
		return repository.findByUserId(userId);
	}
}