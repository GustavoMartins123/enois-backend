package com.socialcoisa.rede.support.adapters.in.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.socialcoisa.rede.shared.web.AdminGuard;
import com.socialcoisa.rede.shared.web.CurrentUser;
import com.socialcoisa.rede.shared.web.CurrentUserResolver;
import com.socialcoisa.rede.support.application.SupportService;
import com.socialcoisa.rede.support.domain.SupportCategory;
import com.socialcoisa.rede.support.domain.SupportRequest;
import com.socialcoisa.rede.support.domain.SupportStatus;

@RestController
class SupportController {

	private final SupportService supportService;
	private final CurrentUserResolver currentUserResolver;
	private final AdminGuard adminGuard;
	private final String supportEmail;

	SupportController(SupportService supportService, CurrentUserResolver currentUserResolver, AdminGuard adminGuard,
			@org.springframework.beans.factory.annotation.Value("${rede.support.email:}") String supportEmail) {
		this.supportService = supportService;
		this.currentUserResolver = currentUserResolver;
		this.adminGuard = adminGuard;
		this.supportEmail = supportEmail;
	}

	/**
	 * Canal de contato publico. Serve para a pagina /contato oferecer o e-mail do time a quem ainda
	 * nao tem conta (o formulario exige sessao). Devolve null quando o time nao configurou um e-mail.
	 */
	@GetMapping("/support/config")
	SupportConfigView config() {
		return new SupportConfigView(StringUtils.hasText(supportEmail) ? supportEmail.trim() : null);
	}

	/** Abre um pedido de ajuda para ser atendido por uma pessoa do time. */
	@PostMapping("/support/requests")
	ResponseEntity<SupportRequestView> open(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestBody CreateSupportRequest request) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		SupportRequest created = supportService.open(user.id().value(), request.subject(), request.message(),
			request.contactEmail(), request.imageUrl(), request.category());
		return ResponseEntity.status(HttpStatus.CREATED).body(SupportRequestView.from(created));
	}

	/** Fila do painel central: filtros por categoria e status (apenas administradores). */
	@GetMapping("/admin/support")
	List<SupportRequestView> list(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestParam(required = false) SupportCategory category,
			@RequestParam(required = false) SupportStatus status,
			@RequestParam(defaultValue = "50") int limit) {
		adminGuard.requireAdmin(currentUserResolver.resolve(userId, sessionToken));
		return supportService.list(category, status, limit).stream()
			.map(SupportRequestView::from)
			.toList();
	}

	/** Triagem: muda status e/ou categoria de um ticket (apenas administradores). */
	@PatchMapping("/admin/support/{id}")
	SupportRequestView update(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String id, @RequestBody UpdateSupportRequest request) {
		adminGuard.requireAdmin(currentUserResolver.resolve(userId, sessionToken));
		return SupportRequestView.from(supportService.update(id, request.status(), request.category()));
	}

	/** Resposta oficial ao solicitante: grava, envia por e-mail e notifica no app. */
	@PostMapping("/admin/support/{id}/reply")
	SupportRequestView reply(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String id, @RequestBody ReplySupportRequest request) {
		CurrentUser admin = currentUserResolver.resolve(userId, sessionToken);
		adminGuard.requireAdmin(admin);
		return SupportRequestView.from(supportService.reply(id, admin.id().value(), request.message()));
	}

	record SupportConfigView(String email) {
	}

	record CreateSupportRequest(String subject, String message, String contactEmail, String imageUrl,
			SupportCategory category) {
	}

	record UpdateSupportRequest(SupportStatus status, SupportCategory category) {
	}

	record ReplySupportRequest(String message) {
	}

	record SupportRequestView(String id, String userId, String subject, String message, String contactEmail,
			String imageUrl, String category, String status, String adminReply, String repliedBy,
			String repliedAt, String createdAt) {
		static SupportRequestView from(SupportRequest request) {
			return new SupportRequestView(request.id(), request.userId(), request.subject(), request.message(),
				request.contactEmail(), request.imageUrl(), request.category().name(), request.status().name(),
				request.adminReply(), request.repliedBy(),
				request.repliedAt() == null ? null : request.repliedAt().toString(),
				request.createdAt().toString());
		}
	}
}
