package com.socialcoisa.rede.support.adapters.in.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
import com.socialcoisa.rede.support.domain.SupportRequest;
import com.socialcoisa.rede.support.domain.SupportStatus;

@RestController
class SupportController {

	private final SupportService supportService;
	private final CurrentUserResolver currentUserResolver;
	private final AdminGuard adminGuard;

	SupportController(SupportService supportService, CurrentUserResolver currentUserResolver, AdminGuard adminGuard) {
		this.supportService = supportService;
		this.currentUserResolver = currentUserResolver;
		this.adminGuard = adminGuard;
	}

	/** Abre um pedido de ajuda para ser atendido por uma pessoa do time. */
	@PostMapping("/support/requests")
	ResponseEntity<SupportRequestView> open(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestBody CreateSupportRequest request) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		SupportRequest created = supportService.open(user.id().value(), request.subject(), request.message(),
			request.contactEmail());
		return ResponseEntity.status(HttpStatus.CREATED).body(SupportRequestView.from(created));
	}

	/** Fila de pedidos para o time de suporte (apenas administradores). */
	@GetMapping("/admin/support")
	List<SupportRequestView> list(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestParam(defaultValue = "20") int limit) {
		adminGuard.requireAdmin(currentUserResolver.resolve(userId, sessionToken));
		return supportService.listByStatus(SupportStatus.OPEN, limit).stream()
			.map(SupportRequestView::from)
			.toList();
	}

	record CreateSupportRequest(String subject, String message, String contactEmail) {
	}

	record SupportRequestView(String id, String subject, String message, String contactEmail, String status,
			String createdAt) {
		static SupportRequestView from(SupportRequest request) {
			return new SupportRequestView(request.id(), request.subject(), request.message(), request.contactEmail(),
				request.status().name(), request.createdAt().toString());
		}
	}
}
