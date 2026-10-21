package com.socialcoisa.rede.freelances.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.freelances.application.port.out.FreelanceRepository;
import com.socialcoisa.rede.freelances.domain.Freelance;
import com.socialcoisa.rede.profile.application.port.out.UserProfileRepository;
import com.socialcoisa.rede.shared.age.AgeGate;
import com.socialcoisa.rede.shared.security.ActionThrottle;
import com.socialcoisa.rede.shared.web.ApiException;

/** Freelance: escopo fechado + faixa de valor + prazo. Sem nível/local/vaga externa. */
@Service
public class FreelanceService {

	private static final int PER_HOUR = 10;
	private final FreelanceRepository freelances;
	private final UserProfileRepository profiles;
	private final ActionThrottle throttle;
	private final AgeGate ageGate;
	private final Clock clock;

	public FreelanceService(FreelanceRepository freelances, UserProfileRepository profiles,
			ActionThrottle throttle, AgeGate ageGate, Clock clock) {
		this.freelances = freelances;
		this.profiles = profiles;
		this.throttle = throttle;
		this.ageGate = ageGate;
		this.clock = clock;
	}

	@Transactional
	public Freelance create(String userId, String title, String scope, int budgetMin, int budgetMax,
			String deadline) {
		ageGate.requireCanWrite(userId);
		throttle.exigePermitido("freelance", userId, PER_HOUR, java.time.Duration.ofHours(1));
		if (!StringUtils.hasText(title) || title.trim().length() < 3) {
			throw new ApiException(400, "INVALID_TITLE", "Título do freela precisa de pelo menos 3 caracteres.");
		}
		if (!StringUtils.hasText(scope) || scope.trim().length() < 20) {
			throw new ApiException(400, "INVALID_SCOPE",
				"Descreva o escopo fechado em pelo menos 20 caracteres.");
		}
		if (budgetMin <= 0 || budgetMax < budgetMin) {
			throw new ApiException(400, "INVALID_BUDGET", "Informe uma faixa de valor válida.");
		}
		LocalDate date;
		try {
			date = LocalDate.parse(deadline == null ? "" : deadline.trim());
		}
		catch (DateTimeParseException invalid) {
			throw new ApiException(400, "INVALID_DEADLINE", "Informe uma data de prazo válida.");
		}
		if (date.isBefore(LocalDate.now(clock))) {
			throw new ApiException(400, "INVALID_DEADLINE", "O prazo precisa ser hoje ou no futuro.");
		}
		Freelance f = Freelance.create("free_" + UUID.randomUUID().toString().replace("-", ""), userId,
			title.trim(), scope.trim(), budgetMin, budgetMax, date, clock.instant());
		return freelances.save(f);
	}

	@Transactional
	public Freelance close(String id, String userId) {
		Freelance f = freelances.findById(id)
			.orElseThrow(() -> new ApiException(404, "FREELANCE_NOT_FOUND", "Freela não encontrado."));
		if (!f.ownedBy(userId)) {
			throw new ApiException(403, "NOT_OWNER", "Este freela não é seu.");
		}
		if (f.status() == Freelance.Status.CLOSED) {
			throw new ApiException(409, "ALREADY_CLOSED", "Este freela já está encerrado.");
		}
		return freelances.save(f.close(clock.instant()));
	}

	@Transactional(readOnly = true)
	public List<Freelance> list(int limit, int offset) {
		return freelances.findOpen(Math.min(60, Math.max(1, limit)), Math.max(0, offset));
	}

	@Transactional(readOnly = true)
	public List<Freelance> mine(String userId) {
		return freelances.findByUser(userId);
	}

	@Transactional(readOnly = true)
	public String usernameOf(String userId) {
		return profiles.findById(userId).map(p -> p.username()).orElse(null);
	}
}
