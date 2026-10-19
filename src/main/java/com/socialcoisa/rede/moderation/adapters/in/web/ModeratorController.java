package com.socialcoisa.rede.moderation.adapters.in.web;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.socialcoisa.rede.games.application.GameService;
import com.socialcoisa.rede.games.domain.Game;
import com.socialcoisa.rede.moderation.application.ModeratorService;
import com.socialcoisa.rede.moderation.application.SanctionProposalService;
import com.socialcoisa.rede.moderation.domain.ModerationVoteType;
import com.socialcoisa.rede.moderation.domain.ModeratorApplicationStatus;
import com.socialcoisa.rede.moderation.domain.SanctionType;
import com.socialcoisa.rede.moderation.domain.SanctionVoteType;
import com.socialcoisa.rede.profile.application.port.out.UserProfileRepository;
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
	private final SanctionProposalService sanctionProposals;
	private final GameService games;
	private final UserProfileRepository profiles;
	private final CurrentUserResolver currentUserResolver;
	private final AdminGuard adminGuard;

	ModeratorController(ModeratorService moderators, SanctionProposalService sanctionProposals,
			GameService games, UserProfileRepository profiles, CurrentUserResolver currentUserResolver,
			AdminGuard adminGuard) {
		this.moderators = moderators;
		this.sanctionProposals = sanctionProposals;
		this.games = games;
		this.profiles = profiles;
		this.currentUserResolver = currentUserResolver;
		this.adminGuard = adminGuard;
	}

	@GetMapping("/moderation/me")
	MeResponse me(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader("X-Session-Token") String sessionToken) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		ModeratorService.MeView me = moderators.me(user.id().value());
		return new MeResponse(me.reputation(), me.minReputation(), me.eligible(), me.moderator(),
			adminGuard.isAdmin(user), me.moderatorCount(), me.minModeratorsForSanction(), me.application());
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

	@GetMapping("/admin/moderators")
	List<ModeratorService.ApplicationView> moderatorList(
			@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader("X-Session-Token") String sessionToken,
			@RequestParam(defaultValue = "200") int limit) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		adminGuard.requireAdmin(user);
		return moderators.approvedModerators(limit);
	}

	@PostMapping("/admin/moderators")
	ModeratorService.ApplicationView appoint(
			@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader("X-Session-Token") String sessionToken,
			@RequestBody AppointmentRequest request) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		adminGuard.requireAdmin(user);
		return moderators.appoint(request.username(), user.id().value(), request.note());
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

	@GetMapping("/moderation/sanction-proposals")
	List<SanctionProposalService.ProposalView> sanctionProposals(
			@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader("X-Session-Token") String sessionToken,
			@RequestParam(defaultValue = "50") int limit) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		requireModerator(user);
		return sanctionProposals.pending(user.id().value(), limit);
	}

	@PostMapping("/moderation/sanction-proposals")
	SanctionProposalService.ProposalView proposeSanction(
			@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader("X-Session-Token") String sessionToken,
			@RequestBody SanctionProposalRequest request) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		requireModerator(user);
		return sanctionProposals.propose(user.id().value(), request.username(), request.type(), request.reason());
	}

	@PostMapping("/moderation/sanction-proposals/{proposalId}/vote")
	SanctionProposalService.DecisionResult voteSanction(
			@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader("X-Session-Token") String sessionToken, @PathVariable String proposalId,
			@RequestBody SanctionProposalVoteRequest request) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		requireModerator(user);
		return sanctionProposals.vote(proposalId, user.id().value(), request.vote(), request.note());
	}

	@PostMapping("/admin/sanction-proposals/{proposalId}/cancel")
	SanctionProposalService.ProposalView cancelSanction(
			@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader("X-Session-Token") String sessionToken, @PathVariable String proposalId) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		adminGuard.requireAdmin(user);
		return sanctionProposals.cancel(proposalId, user.id().value());
	}

	@GetMapping("/moderation/games")
	List<GameReviewView> pendingGames(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader("X-Session-Token") String sessionToken,
			@RequestParam(defaultValue = "30") int limit) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		requireModerator(user);
		return games.pending(limit).stream().map(this::gameView).toList();
	}

	@PostMapping("/moderation/games/{gameId}/approve")
	Map<String, Object> approveGame(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader("X-Session-Token") String sessionToken, @PathVariable String gameId,
			@RequestBody(required = false) DecisionRequest request) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		requireModerator(user);
		Game approved = games.approve(gameId, user.id().value(), request == null ? null : request.note());
		return Map.of("id", approved.id(), "status", approved.status().name());
	}

	@PostMapping("/moderation/games/{gameId}/reject")
	Map<String, Object> rejectGame(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader("X-Session-Token") String sessionToken, @PathVariable String gameId,
			@RequestBody(required = false) DecisionRequest request) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		requireModerator(user);
		Game rejected = games.reject(gameId, user.id().value(), request == null ? null : request.note());
		return Map.of("id", rejected.id(), "status", rejected.status().name());
	}

	private GameReviewView gameView(Game game) {
		String username = profiles.findById(game.userId()).map(profile -> profile.username()).orElse(null);
		return new GameReviewView(game.id(), game.userId(), username, game.title(), game.description(),
			game.cover(), game.url(), game.tags(), game.aspect() == null ? null : game.aspect().name(),
			game.submittedAt() == null ? null : game.submittedAt().toString());
	}

	private void requireModerator(CurrentUser user) {
		if (!adminGuard.isAdmin(user) && !moderators.isModerator(user.id().value())) {
			throw new ApiException(403, "FORBIDDEN", "Acesso restrito a moderadores.");
		}
	}

	record MeResponse(int reputation, int minReputation, boolean eligible, boolean moderator,
			boolean admin, long moderatorCount, int minModeratorsForSanction,
			ModeratorService.ApplicationView application) {
	}

	record ApplicationRequest(String motivation, String experience, String availability) {
	}

	record DecisionRequest(String note) {
	}

	record AppointmentRequest(String username, String note) {
	}

	record VoteRequest(ModerationVoteType vote, String note) {
	}

	record SanctionRequest(SanctionType type, String reason) {
	}

	record SanctionProposalRequest(String username, SanctionType type, String reason) {
	}

	record SanctionProposalVoteRequest(SanctionVoteType vote, String note) {
	}

	record GameReviewView(String id, String userId, String username, String title, String description,
			String cover, String url, String tags, String aspect, String submittedAt) {
	}
}
