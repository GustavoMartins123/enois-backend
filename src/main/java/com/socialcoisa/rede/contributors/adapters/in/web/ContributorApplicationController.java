package com.socialcoisa.rede.contributors.adapters.in.web;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.socialcoisa.rede.contributors.application.ContributorApplicationCommand;
import com.socialcoisa.rede.contributors.application.ContributorApplicationService;
import com.socialcoisa.rede.contributors.domain.ContributorApplication;
import com.socialcoisa.rede.contributors.domain.ContributorApplicationStatus;
import com.socialcoisa.rede.contributors.domain.ContributorExperience;
import com.socialcoisa.rede.shared.security.ClientIpResolver;
import com.socialcoisa.rede.shared.security.IpRateLimiter;
import com.socialcoisa.rede.shared.web.AdminGuard;
import com.socialcoisa.rede.shared.web.ApiException;
import com.socialcoisa.rede.shared.web.CurrentUserResolver;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Formulário público de contribuição da landing + fila interna para documentar quem se apresentou.
 *
 * <p>O POST é público de propósito (a landing é aberta), mas passa pelo limite por IP e pelo gate
 * de origem. A listagem e a triagem são apenas para administradores.</p>
 */
@RestController
class ContributorApplicationController {

	private final ContributorApplicationService service;
	private final CurrentUserResolver currentUserResolver;
	private final AdminGuard adminGuard;
	private final IpRateLimiter rateLimiter;
	private final ClientIpResolver clientIpResolver;

	ContributorApplicationController(ContributorApplicationService service,
			CurrentUserResolver currentUserResolver, AdminGuard adminGuard, IpRateLimiter rateLimiter,
			ClientIpResolver clientIpResolver) {
		this.service = service;
		this.currentUserResolver = currentUserResolver;
		this.adminGuard = adminGuard;
		this.rateLimiter = rateLimiter;
		this.clientIpResolver = clientIpResolver;
	}

	@PostMapping("/contributors/applications")
	ResponseEntity<ApplicationView> apply(@RequestBody ApplyRequest request, HttpServletRequest http) {
		if (!rateLimiter.tryAcquire(IpRateLimiter.CONTRIBUTION, clientIpResolver.resolve(http))) {
			throw new ApiException(429, "RATE_LIMITED", "Muitas tentativas. Tente de novo mais tarde.");
		}
		ContributorApplication created = service.apply(new ContributorApplicationCommand(request.name(),
			request.github(), request.email(), request.area(), request.experience(), request.message(),
			request.acceptedTerms()));
		return ResponseEntity.status(HttpStatus.CREATED).body(ApplicationView.from(created));
	}

	@GetMapping("/admin/contributors/applications")
	List<AdminView> list(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestParam(required = false) ContributorApplicationStatus status,
			@RequestParam(defaultValue = "100") int limit) {
		adminGuard.requireAdmin(currentUserResolver.resolve(sessionToken));
		return service.list(status, limit).stream().map(AdminView::from).toList();
	}

	@PatchMapping("/admin/contributors/applications/{id}")
	AdminView updateStatus(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String id, @RequestBody UpdateStatusRequest request) {
		adminGuard.requireAdmin(currentUserResolver.resolve(sessionToken));
		return AdminView.from(service.updateStatus(id, request.status()));
	}

	record ApplyRequest(String name, String github, String email, String area, String experience, String message,
			boolean acceptedTerms) {
	}

	record UpdateStatusRequest(ContributorApplicationStatus status) {
	}

	/** Resposta pública: não devolve e-mail nem mensagem; só o protocolo e o estado inicial. */
	record ApplicationView(String id, ContributorApplicationStatus status, Instant createdAt) {
		static ApplicationView from(ContributorApplication application) {
			return new ApplicationView(application.id(), application.status(), application.createdAt());
		}
	}

	/** Visão interna para documentação/triagem. */
	record AdminView(String id, String name, String github, String email, String area,
			ContributorExperience experience, String message, ContributorApplicationStatus status,
			Instant acceptedTermsAt, Instant createdAt) {
		static AdminView from(ContributorApplication application) {
			return new AdminView(application.id(), application.name(), application.github(), application.email(),
				application.area(), application.experience(), application.message(), application.status(),
				application.acceptedTermsAt(), application.createdAt());
		}
	}
}
