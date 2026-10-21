package com.socialcoisa.rede.age.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.age.application.port.out.AgeAuditRepository;
import com.socialcoisa.rede.age.application.port.out.ParentalLinkRepository;
import com.socialcoisa.rede.age.domain.ParentalLink;
import com.socialcoisa.rede.identity.application.EmailService;
import com.socialcoisa.rede.profile.application.port.out.UserProfileRepository;
import com.socialcoisa.rede.profile.domain.UserProfile;
import com.socialcoisa.rede.shared.age.AgePolicyReader;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Supervisao parental do ECA Digital.
 *
 * Regras centrais: so o proprio menor inicia o convite; o aceite exige um ADULTO com data de
 * nascimento declarada e conta propria; o vinculo pode ser revogado pelo responsavel. O menor
 * nunca marca "meu responsavel autorizou" — a autorizacao nasce de um aceite autenticado.
 */
@Service
public class ParentalConsentService {

	/** Versao dos termos de supervisao aceitos; sobe quando o texto mudar. */
	static final String TERMS_VERSION = "2026-10";

	private static final Duration INVITE_TTL = Duration.ofDays(7);

	private final ParentalLinkRepository links;
	private final AgePolicyReader agePolicy;
	private final UserProfileRepository profiles;
	private final EmailService emailService;
	private final AgeAuditRepository audit;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();

	public ParentalConsentService(ParentalLinkRepository links, AgePolicyReader agePolicy,
			UserProfileRepository profiles, EmailService emailService, AgeAuditRepository audit, Clock clock) {
		this.links = links;
		this.agePolicy = agePolicy;
		this.profiles = profiles;
		this.emailService = emailService;
		this.audit = audit;
		this.clock = clock;
	}

	/** O menor convida um responsavel por e-mail. Um convite pendente por vez. */
	@Transactional
	public InviteView invite(String minorUserId, String guardianEmail) {
		requireMinor(minorUserId);
		UserProfile minor = profiles.findById(minorUserId)
			.orElseThrow(() -> new ApiException(404, "USER_NOT_FOUND", "Usuario nao encontrado."));
		if (guardianEmail == null || !guardianEmail.contains("@")
				|| guardianEmail.trim().length() > 255) {
			throw new ApiException(400, "INVALID_EMAIL", "Informe um e-mail valido do responsavel.");
		}
		String email = guardianEmail.trim().toLowerCase(Locale.ROOT);
		if (email.equalsIgnoreCase(minor.email())) {
			throw new ApiException(400, "SAME_EMAIL", "O e-mail do responsavel precisa ser diferente do seu.");
		}
		if (links.findActiveByMinor(minorUserId).isPresent()) {
			throw new ApiException(409, "ALREADY_SUPERVISED", "Sua conta ja tem um responsavel autorizado.");
		}

		Instant now = clock.instant();
		links.cancelPendingByMinor(minorUserId);

		String rawToken = newToken();
		ParentalLink link = new ParentalLink("pl_" + UUID.randomUUID().toString().replace("-", ""), minorUserId, null,
			email, ParentalLink.Status.PENDING, sha256(rawToken), TERMS_VERSION, now, now.plus(INVITE_TTL), null,
			null);
		links.save(link);
		boolean sent = emailService.sendParentalConsentEmail(email, minor.name(), rawToken);
		audit.record(minorUserId, "PARENTAL_INVITE_CREATED", minorUserId, "convite enviado para responsavel");
		return new InviteView(link.status(), link.expiresAt(), sent);
	}

	/** O responsavel autenticado aceita o convite (adulto, com data declarada, conta propria). */
	@Transactional
	public void accept(String guardianUserId, String token) {
		ParentalLink link = pendingLink(token);
		requireAdultGuardian(guardianUserId);
		if (guardianUserId.equals(link.minorUserId())) {
			throw new ApiException(403, "INVALID_GUARDIAN", "O proprio menor nao pode aceitar o convite.");
		}
		if (links.findActiveByMinor(link.minorUserId()).isPresent()) {
			throw new ApiException(409, "ALREADY_SUPERVISED", "Este menor ja tem um responsavel autorizado.");
		}
		Instant now = clock.instant();
		links.save(link.acceptedBy(guardianUserId, now));
		audit.record(link.minorUserId(), "PARENTAL_CONSENT_ACCEPTED", guardianUserId, "autorizacao aceita");
	}

	/** O responsavel recusa o convite. */
	@Transactional
	public void reject(String guardianUserId, String token) {
		ParentalLink link = pendingLink(token);
		requireAdultGuardian(guardianUserId);
		Instant now = clock.instant();
		links.save(link.rejectedBy(guardianUserId, now));
		audit.record(link.minorUserId(), "PARENTAL_CONSENT_REJECTED", guardianUserId, "autorizacao recusada");
	}

	/** O responsavel revoga a autorizacao; o menor volta a ficar bloqueado para escrita. */
	@Transactional
	public void revoke(String guardianUserId, String minorUserId) {
		ParentalLink link = links.findActiveByMinor(minorUserId)
			.orElseThrow(() -> new ApiException(404, "NO_SUPERVISION", "Nao ha supervisao ativa para este perfil."));
		if (!guardianUserId.equals(link.guardianUserId())) {
			throw new ApiException(403, "NOT_YOUR_MINOR", "Voce nao e o responsavel autorizado deste perfil.");
		}
		links.save(link.revoked(clock.instant()));
		audit.record(minorUserId, "PARENTAL_CONSENT_REVOKED", guardianUserId, "autorizacao revogada");
	}

	/** Perfis supervisionados por este responsavel (somente os vinculos ativos dele). */
	@Transactional(readOnly = true)
	public List<SupervisedMinorView> supervisedBy(String guardianUserId) {
		return links.findActiveByGuardian(guardianUserId).stream()
			.map(link -> profiles.findById(link.minorUserId()).map(minor -> new SupervisedMinorView(
				minor.id(), minor.username(), minor.name(), link.decidedAt() != null ? link.decidedAt() : link.createdAt()))
				.orElse(null))
			.filter(java.util.Objects::nonNull)
			.toList();
	}

	private ParentalLink pendingLink(String token) {
		if (!StringUtils.hasText(token)) {
			throw new ApiException(400, "INVALID_TOKEN", "Convite obrigatorio.");
		}
		ParentalLink link = links.findByTokenHash(sha256(token.trim()))
			.orElseThrow(() -> new ApiException(404, "INVALID_TOKEN", "Convite invalido."));
		if (!link.isPending()) {
			throw new ApiException(409, "TOKEN_ALREADY_USED", "Este convite ja foi usado.");
		}
		if (link.isExpired(clock.instant())) {
			throw new ApiException(410, "TOKEN_EXPIRED", "Convite expirado. Peca um novo ao menor.");
		}
		return link;
	}

	private void requireMinor(String userId) {
		if (!agePolicy.birthDateDeclared(userId)) {
			throw new ApiException(403, "BIRTH_DATE_REQUIRED",
				"Confirme sua data de nascimento antes de pedir a autorizacao de um responsavel.");
		}
		if (!agePolicy.isMinor(userId)) {
			throw new ApiException(403, "NOT_A_MINOR", "Sua conta nao e de menor de idade.");
		}
	}

	private void requireAdultGuardian(String guardianUserId) {
		if (!agePolicy.birthDateDeclared(guardianUserId) || agePolicy.isMinor(guardianUserId)) {
			throw new ApiException(403, "GUARDIAN_MUST_BE_ADULT",
				"O responsavel precisa ter uma conta de adulto com data de nascimento confirmada.");
		}
	}

	private String newToken() {
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private String sha256(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
				.digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (Exception e) {
			throw new ApiException(500, "HASH_FAILURE", "Nao foi possivel processar o convite.");
		}
	}

	public record InviteView(ParentalLink.Status status, Instant expiresAt, boolean emailSent) {
	}

	public record SupervisedMinorView(String minorUserId, String username, String name, Instant since) {
	}
}
