package com.socialcoisa.rede.support.application;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.identity.application.EmailService;
import com.socialcoisa.rede.support.application.port.out.SupportRequestRepository;
import com.socialcoisa.rede.support.domain.SupportRequest;
import com.socialcoisa.rede.support.domain.SupportStatus;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Pedidos de suporte do app. O pedido fica registrado (visivel em /admin/support) e, se houver um
 * e-mail de suporte configurado, uma pessoa do time tambem e notificada.
 */
@Service
public class SupportService {

	private static final int MAX_SUBJECT = 160;
	private static final int MAX_MESSAGE = 4000;
	private static final int MIN_MESSAGE = 10;
	private static final int MAX_PER_DAY = 5;

	private final SupportRequestRepository repository;
	private final EmailService emailService;
	private final Clock clock;
	private final String supportEmail;

	public SupportService(SupportRequestRepository repository, EmailService emailService, Clock clock,
			@Value("${rede.support.email:}") String supportEmail) {
		this.repository = repository;
		this.emailService = emailService;
		this.clock = clock;
		this.supportEmail = supportEmail;
	}

	@Transactional
	public SupportRequest open(String userId, String subject, String message, String contactEmail) {
		String cleanSubject = subject == null ? "" : subject.trim();
		String cleanMessage = message == null ? "" : message.trim();
		String cleanEmail = StringUtils.hasText(contactEmail) ? contactEmail.trim().toLowerCase(java.util.Locale.ROOT)
			: null;

		if (!StringUtils.hasText(cleanSubject)) {
			throw new ApiException(400, "INVALID_SUPPORT_REQUEST", "Informe o assunto do pedido.");
		}
		if (cleanSubject.length() > MAX_SUBJECT) {
			throw new ApiException(400, "INVALID_SUPPORT_REQUEST", "Assunto deve ter no maximo 160 caracteres.");
		}
		if (cleanMessage.length() < MIN_MESSAGE) {
			throw new ApiException(400, "INVALID_SUPPORT_REQUEST", "Descreva o pedido com pelo menos 10 caracteres.");
		}
		if (cleanMessage.length() > MAX_MESSAGE) {
			throw new ApiException(400, "INVALID_SUPPORT_REQUEST", "Mensagem deve ter no maximo 4000 caracteres.");
		}
		if (cleanEmail == null || !cleanEmail.contains("@")) {
			throw new ApiException(400, "INVALID_SUPPORT_REQUEST", "Informe um e-mail de contato valido.");
		}
		if (repository.countByUserSince(userId, clock.instant().minus(Duration.ofDays(1))) >= MAX_PER_DAY) {
			throw new ApiException(429, "TOO_MANY_SUPPORT_REQUESTS",
				"Voce ja abriu varios pedidos nas ultimas 24h. Aguarde o retorno do time.");
		}

		SupportRequest request = repository.save(SupportRequest.create(
			"support_" + UUID.randomUUID().toString().replace("-", ""), userId, cleanSubject, cleanMessage, cleanEmail,
			clock.instant()));

		if (StringUtils.hasText(supportEmail)) {
			emailService.sendSupportRequestEmail(supportEmail, request.subject(), request.message(), cleanEmail);
		}
		return request;
	}

	@Transactional(readOnly = true)
	public List<SupportRequest> listByStatus(SupportStatus status, int limit) {
		return repository.findByStatus(status, limit < 1 ? 20 : Math.min(limit, 100));
	}
}
