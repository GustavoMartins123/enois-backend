package com.socialcoisa.rede.coin.application.port.out;

import java.util.List;
import java.util.Optional;

import com.socialcoisa.rede.coin.domain.CoinAccount;
import com.socialcoisa.rede.coin.domain.CoinEntry;

public interface CoinLedgerRepository {

	Optional<CoinAccount> findAccount(String accountId);

	CoinAccount insertAccount(CoinAccount account);

	/** Atualiza o snapshot com versão otimista; devolve null quando a versão conflitou. */
	CoinAccount updateBalance(String accountId, long newBalance, long expectedVersion, long nextVersion);

	Optional<CoinEntry> lastEntry(String accountId);

	CoinEntry insertEntry(CoinEntry entry);

	boolean existsByKindAndRefId(com.socialcoisa.rede.coin.domain.CoinEntry.Kind kind, String refId);

	List<CoinEntry> recentEntries(String accountId, int limit);

	/** Total transferido por uma conta num dia (para o teto de contas novas). */
	long transferredOn(String accountId, java.time.Instant startOfDay, java.time.Instant endOfDay);

	boolean accountExistsForUser(String userId);
}
