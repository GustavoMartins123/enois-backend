package com.socialcoisa.rede.moderation.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.identity.application.port.out.AuthSessionRepository;
import com.socialcoisa.rede.moderation.application.port.out.BlockedIpRepository;
import com.socialcoisa.rede.moderation.domain.BlockedIp;
import com.socialcoisa.rede.profile.application.port.out.UserProfileRepository;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Bloqueio de IP pela moderacao (V115). Bloquear um IP impede signup, login e emissao de sessao
 * daquele endereco — a defesa contra o spammer que volta com conta nova do mesmo lugar.
 *
 * <p>Complemento do banimento: a conta e barrada pelo e-mail, o IP e barrado aqui. O painel lista
 * os IPs recentes de um usuario (registrados no login) para o admin escolher o que bloquear.
 */
@Service
public class IpBlockService {

	private final BlockedIpRepository blockedIps;
	private final AuthSessionRepository sessions;
	private final UserProfileRepository profiles;
	private final Clock clock;

	public IpBlockService(BlockedIpRepository blockedIps, AuthSessionRepository sessions,
			UserProfileRepository profiles, Clock clock) {
		this.blockedIps = blockedIps;
		this.sessions = sessions;
		this.profiles = profiles;
		this.clock = clock;
	}

	public record BlockedIpView(String id, String ip, String reason, String createdBy, String createdAt) {
	}

	public record UserIpView(String ip, String lastSeenAt) {
	}

	public BlockedIpView block(String adminId, String ip, String reason) {
		String cleanIp = BlockedIp.cleanIp(ip);
		if (cleanIp.isEmpty() || cleanIp.length() > 64 || "desconhecido".equals(cleanIp)) {
			throw new ApiException(400, "INVALID_IP", "Informe um IP valido para bloquear.");
		}
		if (blockedIps.existsByIp(cleanIp)) {
			throw new ApiException(409, "IP_ALREADY_BLOCKED", "Este IP ja esta bloqueado.");
		}
		if (StringUtils.hasText(reason) && reason.trim().length() < 5) {
			throw new ApiException(400, "REASON_REQUIRED", "Explique o motivo do bloqueio.");
		}
		BlockedIp saved = blockedIps.save(BlockedIp.create("ipblk_" + UUID.randomUUID().toString().replace("-", ""),
			cleanIp, reason, adminId, Instant.now(clock)));
		return toView(saved);
	}

	public void unblock(String ip) {
		blockedIps.deleteByIp(BlockedIp.cleanIp(ip));
	}

	public List<BlockedIpView> list(int limit) {
		return blockedIps.findRecent(limit).stream().map(IpBlockService::toView).toList();
	}

	/** IPs recentes de onde um usuario entrou (do registro de sessoes), mais recentes primeiro. */
	public List<UserIpView> ipsOfUser(String username) {
		var profile = profiles.findByUsername(username.trim())
			.orElseThrow(() -> new ApiException(404, "USER_NOT_FOUND", "Usuario nao encontrado."));
		return sessions.findRecentIpsByUser(profile.id(), 20).stream()
			.filter(ip -> ip != null && !ip.isBlank() && !"desconhecido".equals(ip))
			.distinct()
			.map(ip -> new UserIpView(ip, null))
			.toList();
	}

	private static BlockedIpView toView(BlockedIp blockedIp) {
		return new BlockedIpView(blockedIp.id(), blockedIp.ip(), blockedIp.reason(), blockedIp.createdBy(),
			blockedIp.createdAt().toString());
	}
}
