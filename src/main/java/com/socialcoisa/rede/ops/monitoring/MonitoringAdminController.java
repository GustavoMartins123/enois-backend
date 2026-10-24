package com.socialcoisa.rede.ops.monitoring;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.socialcoisa.rede.shared.web.AdminGuard;
import com.socialcoisa.rede.shared.web.CurrentUserResolver;

/**
 * Painel de monitorabilidade (somente admin). O gate de origem também restringe /admin/** à origem
 * confiável do frontend.
 */
@RestController
@RequestMapping("/admin/monitoring")
class MonitoringAdminController {

	private final MonitoringService monitoring;
	private final CurrentUserResolver currentUserResolver;
	private final AdminGuard adminGuard;

	MonitoringAdminController(MonitoringService monitoring, CurrentUserResolver currentUserResolver,
			AdminGuard adminGuard) {
		this.monitoring = monitoring;
		this.currentUserResolver = currentUserResolver;
		this.adminGuard = adminGuard;
	}

	@GetMapping("/overview")
	MonitoringService.OverviewView overview(
			@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {
		adminGuard.requireAdmin(currentUserResolver.resolve(userId, sessionToken));
		return monitoring.overview();
	}

	@GetMapping("/events")
	List<MonitoringService.EventView> events(
			@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestParam(required = false) String level,
			@RequestParam(required = false) String type,
			@RequestParam(required = false, name = "q") String query,
			@RequestParam(required = false) String from,
			@RequestParam(required = false) String to,
			@RequestParam(required = false) String before,
			@RequestParam(defaultValue = "100") int limit) {
		adminGuard.requireAdmin(currentUserResolver.resolve(userId, sessionToken));
		return monitoring.events(level, type, query, from, to, before, limit);
	}
}
