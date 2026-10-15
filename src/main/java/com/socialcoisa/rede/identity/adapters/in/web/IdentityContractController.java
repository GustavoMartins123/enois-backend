package com.socialcoisa.rede.identity.adapters.in.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.socialcoisa.rede.idempotency.application.CommandResponse;
import com.socialcoisa.rede.idempotency.application.IdempotencyApplicationService;
import com.socialcoisa.rede.identity.application.IdentityVerificationService;
import com.socialcoisa.rede.identity.domain.IdentityVerification;
import com.socialcoisa.rede.jobs.application.JobPublishingPolicy;
import com.socialcoisa.rede.shared.web.CurrentUser;
import com.socialcoisa.rede.shared.web.CurrentUserResolver;

/**
 * Verificacao de identidade civil e elegibilidade para publicar vagas. O estado so muda por retorno
 * do provedor; nao existe rota que o front use para se declarar VERIFIED.
 */
@RestController
@RequestMapping("/identity")
class IdentityContractController {

	private final IdentityVerificationService identityService;
	private final JobPublishingPolicy jobPublishingPolicy;
	private final CurrentUserResolver currentUserResolver;
	private final IdempotencyApplicationService idempotency;

	IdentityContractController(IdentityVerificationService identityService, JobPublishingPolicy jobPublishingPolicy,
			CurrentUserResolver currentUserResolver, IdempotencyApplicationService idempotency) {
		this.identityService = identityService;
		this.jobPublishingPolicy = jobPublishingPolicy;
		this.currentUserResolver = currentUserResolver;
		this.idempotency = idempotency;
	}

	@GetMapping
	IdentityStatusView status(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		return view(identityService.current(user.id().value()));
	}

	@PostMapping("/verification")
	ResponseEntity<String> start(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@RequestBody(required = false) StartVerificationRequest request) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		String consent = request == null ? null : request.consentVersion();
		Map<String, Object> payload = Map.of("consent", consent == null ? "" : consent);
		String hash = idempotency.hash(payload);
		return idempotency.execute(user.id(), "POST", "/api/identity/verification", idempotencyKey, hash, () -> {
			var started = identityService.start(user.id().value(), consent);
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("attemptId", started.attemptId());
			body.put("redirectUrl", started.redirectUrl());
			return new CommandResponse(200, body);
		});
	}

	@PostMapping("/verification/refresh")
	IdentityStatusView refresh(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		return view(identityService.refresh(user.id().value()));
	}

	@DeleteMapping("/verification")
	ResponseEntity<String> revoke(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		String hash = idempotency.hash(Map.of("op", "revoke-identity"));
		return idempotency.execute(user.id(), "DELETE", "/api/identity/verification", idempotencyKey, hash,
			() -> new CommandResponse(200, view(identityService.revoke(user.id().value()))));
	}

	/** Elegibilidade para publicar vaga, com o que falta — a decisao final e sempre do backend. */
	@GetMapping("/eligibility")
	JobPublishingPolicy.Decision eligibility(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		return jobPublishingPolicy.evaluate(user.id().value());
	}

	private IdentityStatusView view(IdentityVerification verification) {
		return new IdentityStatusView(verification.state().name(), verification.state().isVerified(),
			identityService.providerAvailable(), verification.provider(), verification.reasonCode(),
			verification.completedAt() != null ? verification.completedAt().toString() : null);
	}

	record IdentityStatusView(String state, boolean verified, boolean providerAvailable, String provider,
			String reasonCode, String completedAt) {
	}

	record StartVerificationRequest(String consentVersion) {
	}
}
