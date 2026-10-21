package com.socialcoisa.rede.moderation.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.socialcoisa.rede.moderation.application.port.out.SuspiciousUserReader;
import com.socialcoisa.rede.shared.web.AdminGuard;

/**
 * Triagem de contas suspeitas: ranqueia contas recentes por sinais objetivos, sem acusar ninguém
 * automaticamente. O resultado é uma lista para revisão humana; o banimento em lote é uma ação
 * separada e explícita do admin.
 *
 * O score é intencionalmente simples e auditável: cada sinal vale um peso fixo. Padrão no
 * username/nome ("probe", configurável) e nome repetido em massa pegam o caso de cadastro em
 * série; e-mail não confirmado + conta vazia pegam o cadastro descartável.
 */
@Service
public class SuspiciousUserService {

	private static final int MIN_SCORE = 30;
	private static final int MAX_CANDIDATES = 500;
	private static final int MAX_RESULTS = 200;

	private final SuspiciousUserReader reader;
	private final AdminGuard adminGuard;
	private final com.socialcoisa.rede.shared.sanctions.SanctionGateReader sanctionGate;
	private final Clock clock;
	private final int defaultDays;
	private final List<String> namePatterns;

	public SuspiciousUserService(SuspiciousUserReader reader, AdminGuard adminGuard,
			com.socialcoisa.rede.shared.sanctions.SanctionGateReader sanctionGate, Clock clock,
			@Value("${rede.security.suspicious.days:90}") int defaultDays,
			@Value("${rede.security.suspicious.name-patterns:probe}") String patterns) {
		this.reader = reader;
		this.adminGuard = adminGuard;
		this.sanctionGate = sanctionGate;
		this.clock = clock;
		this.defaultDays = Math.max(1, Math.min(defaultDays, 365));
		this.namePatterns = Arrays.stream(patterns.split(","))
			.map(String::trim)
			.filter(pattern -> !pattern.isEmpty())
			.map(pattern -> pattern.toLowerCase(Locale.ROOT))
			.toList();
	}

	@Transactional(readOnly = true)
	public SuspiciousUsersView list(int requestedLimit, int requestedDays) {
		int days = requestedDays <= 0 ? defaultDays : Math.min(requestedDays, 365);
		int limit = Math.max(1, Math.min(requestedLimit, MAX_RESULTS));
		Instant since = clock.instant().minus(Duration.ofDays(days));
		List<SuspiciousUserReader.Candidate> candidates = reader.findRecent(since, MAX_CANDIDATES);

		Map<String, Long> repeatedNames = candidates.stream()
			.collect(Collectors.groupingBy(candidate -> normalize(candidate.name()), Collectors.counting()));

		List<Scored> scored = new ArrayList<>();
		for (var candidate : candidates) {
			if (candidate.moderator() || adminGuard.isAdmin(candidate.email(), candidate.username())) {
				continue;
			}
			// Quem já foi banido não volta para a fila de triagem: a decisão já foi tomada.
			if (sanctionGate.banned(candidate.id())) {
				continue;
			}
			Scored item = score(candidate, repeatedNames.getOrDefault(normalize(candidate.name()), 0L));
			if (item.risk() >= MIN_SCORE) {
				scored.add(item);
			}
		}
		scored.sort(Comparator.comparingInt(Scored::risk).reversed()
			.thenComparing(scoredCandidate -> scoredCandidate.candidate().createdAt(), Comparator.reverseOrder()));

		List<SuspiciousUserView> items = scored.stream()
			.limit(limit)
			.map(item -> view(item.candidate(), item.risk(), item.signals()))
			.toList();
		return new SuspiciousUsersView(items, items.size(), since, days);
	}

	private Scored score(SuspiciousUserReader.Candidate candidate, long sameNameCount) {
		int risk = 0;
		List<String> signals = new ArrayList<>();
		String typed = normalize(candidate.username()) + " " + normalize(candidate.name());

		if (namePatterns.stream().anyMatch(typed::contains)) {
			risk += 70;
			signals.add("NOME_SUSPEITO");
		}
		if (sameNameCount >= 3) {
			risk += 35;
			signals.add("NOME_REPETIDO");
		}
		boolean verified = candidate.emailVerifiedAt() != null;
		if (!verified) {
			risk += 15;
			signals.add("EMAIL_NAO_VERIFICADO");
		}
		boolean noContent = candidate.postCount() == 0 && candidate.commentCount() == 0;
		if (noContent) {
			risk += 10;
			signals.add("SEM_CONTEUDO");
		}
		if (isBlank(candidate.avatar()) && isBlank(candidate.bio())) {
			risk += 5;
			signals.add("PERFIL_VAZIO");
		}
		if (candidate.createdAt().isAfter(clock.instant().minus(Duration.ofDays(7)))) {
			risk += 10;
			signals.add("CONTA_NOVA");
		}
		if (candidate.reportCount() > 0) {
			risk += (int) Math.min(45, candidate.reportCount() * 15);
			signals.add("DENUNCIAS_CONTRA");
		}
		return new Scored(candidate, risk, List.copyOf(signals));
	}

	private SuspiciousUserView view(SuspiciousUserReader.Candidate candidate, int risk, List<String> signals) {
		return new SuspiciousUserView(candidate.username(), candidate.name(), candidate.createdAt(),
			candidate.emailVerifiedAt() != null, candidate.postCount(), candidate.commentCount(),
			candidate.reportCount(), risk, signals);
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private record Scored(SuspiciousUserReader.Candidate candidate, int risk, List<String> signals) {
	}

	public record SuspiciousUsersView(List<SuspiciousUserView> items, int total, Instant since, int days) {
	}

	public record SuspiciousUserView(String username, String name, Instant createdAt, boolean emailVerified,
			long posts, long comments, long reportsAgainst, int riskScore, List<String> signals) {
	}
}
