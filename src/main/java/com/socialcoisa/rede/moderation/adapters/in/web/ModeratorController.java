package com.socialcoisa.rede.moderation.adapters.in.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.socialcoisa.rede.moderation.application.ModeratorService;
import com.socialcoisa.rede.moderation.domain.ModerationVoteType;
import com.socialcoisa.rede.moderation.domain.ModeratorApplicationStatus;
import com.socialcoisa.rede.moderation.domain.SanctionType;
import com.socialcoisa.rede.shared.web.AdminGuard;
import com.socialcoisa.rede.shared.web.ApiException;
import com.socialcoisa.rede.shared.web.CurrentUser;
import com.socialcoisa.rede.shared.web.CurrentUserResolver;

/**
 * Candidatura a moderador + painel de votação + ações de admin do painel.
 *
 * Permissões:
 * - qualquer sessão: ver o próprio estado e enviar candidatura;
 * - moderador (candidatura aprovada) ou admin: listar denúncias e votar;
 * - admin: revisar candidaturas, revogar moderador e punir qualquer usuário.
 */
@RestController
class ModeratorController {

	private final ModeratorService moderators;
	private final CurrentUserResolver currentUserResolver;
	private final AdminGuard adminGuard;

	ModeratorController(ModeratorService moderators, CurrentUserResolver currentUserResolver, AdminGuard adminGuard) {
		this.moderators = moderators;
		this.currentUserResolver = currentUserResolver;
		this.adminGuard = adminGuard;
	}

	@GetMapping("/moderation/me")
	MeResponse me(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader("X-Session-Token") String sessionToken) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		ModeratorService.MeView me = moderators.me(user.id().value());
		return new MeResponse(me.reputation(), me.minReputation(), me.eligible(), me.moderator(),
			adminGuard.isAdmin(user), me.application());
	}

	@PostMapping("/moderation/applications")
	ModeratorService.ApplicationView apply(
			@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader("X-Session-Token") String sessionToken,
			@RequestBody ApplicationRequest request) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		return moderators.apply(user.id().value(), request.motivation(), request.experience(),
			request.availability());
	}

	@GetMapping("/moderation/reports")
	List<ModeratorService.PanelReportView> reports(
			@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader("X-Session-Token") String sessionToken,
			@RequestParam(defaultValue = "20") int limit) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		requireModerator(user);
		return moderators.panelReports(user.id().value(), limit);
	}

	@PostMapping("/moderation/reports/{reportId}/vote")
	ModeratorService.VoteResult vote(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader("X-Session-Token") String sessionToken, @PathVariable String reportId,
			@RequestBody VoteRequest request) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		requireModerator(user);
		return moderators.vote(reportId, user.id().value(), request.vote(), request.note());
	}

	@GetMapping("/admin/moderator-applications")
	List<ModeratorService.ApplicationView> applications(
			@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader("X-Session-Token") String sessionToken,
			@RequestParam(defaultValue = "PENDING") ModeratorApplicationStatus status,
			@RequestParam(defaultValue = "50") int limit) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		adminGuard.requireAdmin(user);
		return moderators.applicationsOfStatus(status, limit);
	}

	@PostMapping("/admin/moderator-applications/{applicationId}/approve")
	ModeratorService.ApplicationView approve(
			@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader("X-Session-Token") String sessionToken, @PathVariable String applicationId,
			@RequestBody(required = false) DecisionRequest request) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		adminGuard.requireAdmin(user);
		return moderators.decideApplication(applicationId, user.id().value(), true,
			request == null ? null : request.note());
	}

	@PostMapping("/admin/moderator-applications/{applicationId}/reject")
	ModeratorService.ApplicationView reject(
			@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader("X-Session-Token") String sessionToken, @PathVariable String applicationId,
			@RequestBody(required = false) DecisionRequest request) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		adminGuard.requireAdmin(user);
		return moderators.decideApplication(applicationId, user.id().value(), false,
			request == null ? null : request.note());
	}

	@PostMapping("/admin/moderators/{userId}/revoke")
	ModeratorService.ApplicationView revoke(
			@RequestHeader(value = "X-User-Id", required = false) String requesterId,
			@RequestHeader("X-Session-Token") String sessionToken, @PathVariable String userId,
			@RequestBody(required = false) DecisionRequest request) {
		CurrentUser user = currentUserResolver.resolve(requesterId, sessionToken);
		adminGuard.requireAdmin(user);
		return moderators.revoke(userId, user.id().value(), request == null ? null : request.note());
	}

	@PostMapping("/admin/users/{username}/sanction")
	ModeratorService.SanctionResult sanction(
			@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader("X-Session-Token") String sessionToken, @PathVariable String username,
			@RequestBody SanctionRequest request) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		adminGuard.requireAdmin(user);
		return moderators.sanctionUser(user.id().value(), username, request.type(), request.reason());
	}

	private void requireModerator(CurrentUser user) {
		if (!adminGuard.isAdmin(user) && !moderators.isModerator(user.id().value())) {
			throw new ApiException(403, "FORBIDDEN", "Acesso restrito a moderadores.");
		}
	}

	record MeResponse(int reputation, int minReputation, boolean eligible, boolean moderator,
			boolean admin, ModeratorService.ApplicationView application) {
	}

	record ApplicationRequest(String motivation, String experience, String availability) {
	}

	record DecisionRequest(String note) {
	}

	record VoteRequest(ModerationVoteType vote, String note) {
	}

	record SanctionRequest(SanctionType type, String reason) {
	}
}
