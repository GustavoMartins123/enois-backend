package com.socialcoisa.rede.moderation.application;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.socialcoisa.rede.moderation.domain.SanctionType;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Banimento em lote para limpeza de contas em série (ex.: cadastros "probe").
 *
 * A ação é deliberadamente parcial: uma conta inválida no meio da lista não cancela as outras.
 * Cada item passa pelo MESMO {@link ModeratorService#sanctionUser} do painel — valida motivo,
 * impede punir a si, evita sancionar duas vezes e revoga as sessões. Não existe atalho de escrita
 * direta em user_sanctions.
 */
@Service
public class BulkBanService {

	private static final int MAX_BATCH = 200;

	private final ModeratorService moderators;
	private final int maxBatch;

	public BulkBanService(ModeratorService moderators,
			@Value("${rede.moderation.bulk-ban-max:200}") int maxBatch) {
		this.moderators = moderators;
		this.maxBatch = Math.max(1, Math.min(maxBatch, MAX_BATCH));
	}

	public BulkBanResult ban(String adminId, List<String> usernames, SanctionType type, String reason) {
		SanctionType sanctionType = type == null ? SanctionType.BAN : type;
		if (sanctionType == SanctionType.RESTRICTION) {
			throw new ApiException(400, "INVALID_SANCTION_TYPE", "O lote aceita banimento, não restrição.");
		}
		if (reason == null || reason.trim().length() < 10) {
			throw new ApiException(400, "REASON_REQUIRED",
				"Explique o motivo do banimento em lote (pelo menos 10 caracteres).");
		}
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
		int banned = 0;
		int alreadyBanned = 0;
		int failed = 0;
		for (String username : unique) {
			try {
				ModeratorService.SanctionResult result = moderators.sanctionUser(adminId, username, sanctionType,
					reason.trim());
				if ("ALREADY_BANNED".equals(result.status())) {
					alreadyBanned++;
					items.add(new BulkBanItemResult(username, "ALREADY_BANNED", "Esta conta já estava banida."));
				}
				else {
					banned++;
					items.add(new BulkBanItemResult(username, "BANNED", "Banida."));
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
		return new BulkBanResult(unique.size(), banned, alreadyBanned, failed, items);
	}

	public record BulkBanResult(int total, int banned, int alreadyBanned, int failed,
			List<BulkBanItemResult> items) {
	}

	public record BulkBanItemResult(String username, String status, String message) {
	}
}
