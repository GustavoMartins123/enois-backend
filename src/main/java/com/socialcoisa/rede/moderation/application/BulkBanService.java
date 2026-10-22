package com.socialcoisa.rede.moderation.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.socialcoisa.rede.identity.application.port.out.AuthSessionRepository;
import com.socialcoisa.rede.moderation.domain.SanctionType;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Banimento em lote para limpeza de contas em série (ex.: cadastros "probe").
 *
 * A ação é deliberadamente parcial: uma conta inválida no meio da lista não cancela as outras.
 * Cada item passa pelo MESMO {@link ModeratorService#sanctionUser} do painel — impede punir a si,
 * evita sancionar duas vezes e revoga as sessões. Não existe atalho de escrita direta em
 * user_sanctions.
 *
 * O MOTIVO É OPCIONAL aqui (decisão da dona, 2026-10-22): o lote é a limpeza de contas em série,
 * onde o sinal já é o próprio nome/comportamento; sem motivo, fica uma trilha padrão. E o IP de
 * cada conta banida entra na lista de bloqueio — é o que impede o próximo lote de nascer do mesmo
 * lugar (o cadastro e o login checam a lista).
 */
@Service
public class BulkBanService {

	private static final int MAX_BATCH = 200;
	private static final String DEFAULT_REASON =
		"Conta suspeita banida em lote no painel de moderação (sem motivo digitado).";
	private static final int IPS_PER_ACCOUNT = 3;

	private final ModeratorService moderators;
	private final AuthSessionRepository sessions;
	private final IpBlockService ipBlocks;
	private final int maxBatch;
	private final boolean blockIps;
	private final int ipsMinAccounts;

	public BulkBanService(ModeratorService moderators, AuthSessionRepository sessions,
			IpBlockService ipBlocks, @Value("${rede.moderation.bulk-ban-max:200}") int maxBatch,
			@Value("${rede.moderation.bulk-ban.block-ips:true}") boolean blockIps,
			@Value("${rede.moderation.bulk-ban.ips-min-accounts:2}") int ipsMinAccounts) {
		this.moderators = moderators;
		this.sessions = sessions;
		this.ipBlocks = ipBlocks;
		this.maxBatch = Math.max(1, Math.min(maxBatch, MAX_BATCH));
		this.blockIps = blockIps;
		this.ipsMinAccounts = Math.max(2, Math.min(ipsMinAccounts, 50));
	}

	public BulkBanResult ban(String adminId, List<String> usernames, SanctionType type, String reason) {
		SanctionType sanctionType = type == null ? SanctionType.BAN : type;
		if (sanctionType == SanctionType.RESTRICTION) {
			throw new ApiException(400, "INVALID_SANCTION_TYPE", "O lote aceita banimento, não restrição.");
		}
		// Motivo é opcional no lote: sem ele, a trilha fica com o texto padrão.
		String cleanReason = reason == null || reason.trim().isEmpty() ? DEFAULT_REASON : reason.trim();
		if (usernames == null || usernames.isEmpty()) {
			throw new ApiException(400, "NO_USERS_SELECTED", "Selecione pelo menos uma conta.");
		}

		Set<String> unique = new LinkedHashSet<>();
		for (String username : usernames) {
			if (username != null && !username.isBlank()) {
				unique.add(username.trim());
			}
		}
		if (unique.size() > maxBatch) {
			throw new ApiException(400, "TOO_MANY_USERS",
				"O lote aceita no máximo " + maxBatch + " contas por vez.");
		}

		List<BulkBanItemResult> items = new ArrayList<>();
		Map<String, Integer> ipsByAccount = new HashMap<>();
		int banned = 0;
		int alreadyBanned = 0;
		int failed = 0;
		for (String username : unique) {
			try {
				ModeratorService.SanctionResult result = moderators.sanctionUser(adminId, username, sanctionType,
					cleanReason);
				if ("ALREADY_BANNED".equals(result.status())) {
					alreadyBanned++;
					items.add(new BulkBanItemResult(username, "ALREADY_BANNED", "Esta conta já estava banida."));
				}
				else {
					banned++;
					items.add(new BulkBanItemResult(username, "BANNED", "Banida."));
					// Guarda de onde a conta nasceu/entrou: o bloqueio do IP é decidido no fim,
					// olhando o lote inteiro (ver blockRepeatedIps).
					rememberIps(result.userId(), ipsByAccount);
				}
			}
			catch (ApiException exception) {
				failed++;
				items.add(new BulkBanItemResult(username, "FAILED", exception.getMessage()));
			}
			catch (RuntimeException exception) {
				failed++;
				items.add(new BulkBanItemResult(username, "FAILED", "Erro ao banir esta conta."));
			}
		}
		List<String> blockedIps = blockRepeatedIps(adminId, ipsByAccount);
		return new BulkBanResult(unique.size(), banned, alreadyBanned, failed, items, blockedIps);
	}

	private void rememberIps(String userId, Map<String, Integer> ipsByAccount) {
		Set<String> seen = new HashSet<>();
		for (String ip : sessions.findRecentIpsByUser(userId, IPS_PER_ACCOUNT)) {
			if (ip == null || ip.isBlank() || "desconhecido".equals(ip) || !seen.add(ip)) {
				continue;
			}
			ipsByAccount.merge(ip, 1, Integer::sum);
		}
	}

	/**
	 * Bloqueia só o IP que o lote REPETE (2+ contas banidas no mesmo lote, configurável).
	 *
	 * Não bloqueia o IP de cada conta isolada de propósito: um endereço pode ser NAT de casa,
	 * escola ou operadora móvel — fechar por uma conta só derrubaria gente inocente junto. O
	 * sinal de ataque em série é o mesmo IP gerando VÁRIAS contas, e é esse que a gente fecha.
	 */
	private List<String> blockRepeatedIps(String adminId, Map<String, Integer> ipsByAccount) {
		if (!blockIps) {
			return List.of();
		}
		List<String> blocked = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : ipsByAccount.entrySet()) {
			if (entry.getValue() < ipsMinAccounts) {
				continue;
			}
			try {
				ipBlocks.block(adminId, entry.getKey(),
					entry.getValue() + " contas banidas em lote no painel de moderação.");
				blocked.add(entry.getKey());
			}
			catch (ApiException alreadyBlocked) {
				blocked.add(entry.getKey());
			}
			catch (RuntimeException failure) {
				// IP inválido/registro estranho não pode derrubar o banimento em si.
			}
		}
		return List.copyOf(blocked);
	}

	public record BulkBanResult(int total, int banned, int alreadyBanned, int failed,
			List<BulkBanItemResult> items, List<String> blockedIps) {
	}

	public record BulkBanItemResult(String username, String status, String message) {
	}
}
