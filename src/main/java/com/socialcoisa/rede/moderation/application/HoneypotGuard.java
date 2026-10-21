package com.socialcoisa.rede.moderation.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.feed.domain.UserId;
import com.socialcoisa.rede.identity.application.port.out.AuthSessionRepository;
import com.socialcoisa.rede.moderation.application.port.out.ModeratorApplicationRepository;
import com.socialcoisa.rede.moderation.application.port.out.SanctionRepository;
import com.socialcoisa.rede.moderation.domain.UserSanction;
import com.socialcoisa.rede.shared.web.AdminGuard;
import com.socialcoisa.rede.shared.web.CurrentUser;

/**
 * Campos que humanos não veem e humanos não preenchem. Bot que preenche ganha ban automático.
 *
 * O conteúdo NUNCA é gravado: quem chamou devolve uma resposta de sucesso falsa para não ensinar
 * o bot a contornar. Admin/moderador é ignorado (pode ser ferramenta/accessibility) e quem já tem
 * sanção ativa não recebe uma segunda linha.
 */
@Service
public class HoneypotGuard {

	private static final Logger LOG = LoggerFactory.getLogger(HoneypotGuard.class);

	private final SanctionService sanctions;
	private final SanctionRepository sanctionRepository;
	private final AuthSessionRepository sessions;
	private final AdminGuard adminGuard;
	private final ModeratorApplicationRepository moderators;
	private final Clock clock;
	private final boolean autoBan;

	public HoneypotGuard(SanctionService sanctions, SanctionRepository sanctionRepository,
			AuthSessionRepository sessions, AdminGuard adminGuard, ModeratorApplicationRepository moderators,
			Clock clock, @Value("${rede.security.honeypot-auto-ban:true}") boolean autoBan) {
		this.sanctions = sanctions;
		this.sanctionRepository = sanctionRepository;
		this.sessions = sessions;
		this.adminGuard = adminGuard;
		this.moderators = moderators;
		this.clock = clock;
		this.autoBan = autoBan;
	}

	/** Devolve true quando o campo-armadilha veio preenchido (a requisição deve ser descartada). */
	public boolean triggered(String userId, String value, String context) {
		if (!StringUtils.hasText(value)) {
			return false;
		}
		LOG.warn("Honeypot acionado em {} por {} (auto-ban={})", context, userId, autoBan);
		if (!autoBan) {
			return true;
		}
		CurrentUser user = new CurrentUser(new UserId(userId));
		if (adminGuard.isAdmin(user) || moderators.findApprovedByUser(userId).isPresent()) {
			return true;
		}
		if (sanctionRepository.findActiveByUser(userId).isPresent()) {
			return true;
		}
		String id = "sanc_" + UUID.randomUUID().toString().replace("-", "");
		UserSanction sanction = UserSanction.create(id, userId, "AUTO:HONEYPOT:" + context,
			"Conta automatizada: preencheu um campo invisível de segurança (" + context + ").",
			Instant.now(clock));
		sanctions.banAuthor(sanction, userId, "honeypot", sessions);
		return true;
	}
}
