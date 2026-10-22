package com.socialcoisa.rede.support.application;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.identity.application.EmailService;
import com.socialcoisa.rede.notifications.application.NotificationService;
import com.socialcoisa.rede.support.domain.SupportRequest;

/**
 * Avisa quem abriu o pedido quando ele é concluído ou reaberto. Vale para as duas portas: o
 * painel (que fecha e o GitHub recebe o fechamento) e o GitHub (que fecha e o painel recebe pelo
 * webhook). O chamador só dispara quando o status realmente mudou, então não há aviso duplicado.
 */
@Service
class SupportStatusNotifier {

	private final EmailService emailService;
	private final NotificationService notifications;

	SupportStatusNotifier(EmailService emailService, NotificationService notifications) {
		this.emailService = emailService;
		this.notifications = notifications;
	}

	void closed(SupportRequest request) {
		if (StringUtils.hasText(request.contactEmail())) {
			emailService.sendSupportClosedEmail(request.contactEmail(), request.subject());
		}
		if (StringUtils.hasText(request.userId())) {
			notifications.create(request.userId(), "SUPORTE", "sistema", "Pedido de suporte concluído",
				"Seu pedido \"" + request.subject() + "\" foi concluído. Se precisar, abra um novo pedido.",
				"/suporte", "Ver pedidos");
		}
	}

	void reopened(SupportRequest request) {
		if (StringUtils.hasText(request.userId())) {
			notifications.create(request.userId(), "SUPORTE", "sistema", "Pedido de suporte reaberto",
				"Seu pedido \"" + request.subject() + "\" voltou a ser acompanhado pelo time.",
				"/suporte", "Ver pedidos");
		}
	}
}
