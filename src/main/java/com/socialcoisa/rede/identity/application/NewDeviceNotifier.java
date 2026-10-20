package com.socialcoisa.rede.identity.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.identity.application.port.out.KnownDeviceRepository;
import com.socialcoisa.rede.notifications.application.NotificationService;

/**
 * Verifica se o dispositivo usado no login e conhecido. Se for a primeira vez,
 * registra o fingerprint e envia uma notificacao de alerta ao usuario.
 * Nunca bloqueia o login — so avisa.
 */
@Component
public class NewDeviceNotifier {

	private static final Logger LOG = LoggerFactory.getLogger(NewDeviceNotifier.class);

	private final KnownDeviceRepository devices;
	private final NotificationService notifications;

	public NewDeviceNotifier(KnownDeviceRepository devices, NotificationService notifications) {
		this.devices = devices;
		this.notifications = notifications;
	}

	/**
	 * Chamado apos um login bem-sucedido. Se o fingerprint for desconhecido, registra
	 * e notifica. Falhas sao engolidas — o login ja aconteceu.
	 */
	public void check(String userId, String fingerprint) {
		if (!StringUtils.hasText(fingerprint)) {
			return;
		}
		try {
			if (devices.exists(userId, fingerprint)) {
				return;
			}
			devices.save(userId, fingerprint);
			notifications.create(userId, "SEGURANCA", userId,
				"Novo dispositivo detectado",
				"Um novo dispositivo acessou sua conta. Se nao foi voce, troque sua senha imediatamente.",
				"/configuracoes/seguranca", "Ver seguranca");
			LOG.info("Novo dispositivo registrado para usuario {}", userId);
		} catch (RuntimeException e) {
			LOG.debug("Falha ao verificar dispositivo para {}: {}", userId, e.getMessage());
		}
	}
}
