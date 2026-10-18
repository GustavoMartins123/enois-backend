package com.socialcoisa.rede.coin.application;

import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.socialcoisa.rede.coin.application.port.out.CoinLedgerRepository;
import com.socialcoisa.rede.coin.domain.CoinAccount;
import com.socialcoisa.rede.coin.domain.CoinEntry;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * A faísca: a moeda interna do Énóis.
 *
 * Supply FIXO definido no genesis (nunca há emissão posterior): a tesouraria nasce com um
 * volume interno fixo e é DELA que saem o airdrop (100 por conta) e os ganhos. Transferências
 * P2P movem faíscas entre contas sem criar nem queimar. Estilo Pix: instantânea, por chave
 * (username), irreversível — a moderação reverte com lançamento espelho, nunca DELETE.
 *
 * Integridade: ledger append-only com hash encadeado por conta + snapshot otimista +
 * idempotência por (conta, chave). O total da rede não é público.
 */
@Service
public class CoinService {

	public static final String TREASURY_ACCOUNT = "coin_tesouraria";
	/** Volume interno fixo do genesis. Não é público; nunca muda. */
	private static final long TREASURY_GENESIS = 2_000_000L;
	public static final long AIRDROP_PER_USER = 100L;
	public static final long AIRDROP_OWNER = 150_000L;
	/** Teto de envio para contas com menos de 7 dias. */
	private static final long NEW_ACCOUNT_DAILY_CAP = 500L;
	private static final Duration NEW_ACCOUNT_WINDOW = Duration.ofDays(7);

	private static final String OWNER_USERNAME = "aminadojava";

	private final CoinLedgerRepository ledger;
	private final Clock clock;

	public CoinService(CoinLedgerRepository ledger, Clock clock) {
		this.ledger = ledger;
		this.clock = clock;
	}

	public record Wallet(long balance, String chave, List<CoinEntry> entries) {
	}

	/** Conta da moeda: cria a conta e, na primeira vez, executa o airdrop do genesis. */
	@Transactional
	public Wallet wallet(String accountId, String username) {
		CoinAccount account = ensureAccount(accountId);
		ensureGenesis(accountId, username);
		return new Wallet(ensureAccount(accountId).balance(), username,
			ledger.recentEntries(accountId, 50));
	}

	/** Airdrop idempotente: 100 faíscas da tesouraria (150.000 para a dona), uma vez por conta. */
	private void ensureGenesis(String accountId, String username) {
		if (ledger.existsByKindAndRefId(com.socialcoisa.rede.coin.domain.CoinEntry.Kind.AIRDROP,
				"airdrop:" + accountId)) {
			return;
		}
		ensureTreasury();
		long amount = OWNER_USERNAME.equals(username) ? AIRDROP_OWNER : AIRDROP_PER_USER;
		grant(TREASURY_ACCOUNT, -amount, CoinEntry.Kind.GENESIS_TREASURY, "airdrop-tesouraria:" + accountId,
			"Distribuição inicial", Instant.now(clock));
		grant(accountId, amount, CoinEntry.Kind.AIRDROP, "airdrop:" + accountId, "Distribuição inicial",
			Instant.now(clock));
	}

	/** Conta da tesouraria: nasce com o volume fixo do genesis na primeira vez que é tocada. */
	private CoinAccount ensureTreasury() {
		CoinAccount treasury = ledger.findAccount(TREASURY_ACCOUNT)
			.orElseGet(() -> {
				CoinAccount created = ledger.insertAccount(CoinAccount.create(TREASURY_ACCOUNT, 0, Instant.now(clock)));
				grant(TREASURY_ACCOUNT, TREASURY_GENESIS, CoinEntry.Kind.GENESIS_TREASURY, "genesis:tesouraria",
					"Gênese da faísca (supply fixo)", Instant.now(clock));
				return ledger.findAccount(TREASURY_ACCOUNT).orElse(created);
			});
		return treasury;
	}

	private CoinAccount ensureAccount(String accountId) {
		return ledger.findAccount(accountId)
			.orElseGet(() -> ledger.insertAccount(CoinAccount.create(accountId, 0, Instant.now(clock))));
	}

	/** Lança uma entrada na conta (débito se amount < 0) atualizando o snapshot com hash encadeado. */
	private CoinEntry grant(String accountId, long amount, CoinEntry.Kind kind, String refId, String message,
			Instant now) {
		ensureAccount(accountId);
		CoinAccount account = ledger.findAccount(accountId)
			.orElseThrow(() -> new ApiException(500, "COIN_ACCOUNT_MISSING", "Conta da moeda nao encontrada."));
		if (amount < 0 && account.balance() + amount < 0) {
			throw new ApiException(400, "INSUFFICIENT_SPARKS", "Faíscas insuficientes.");
		}
		CoinEntry last = ledger.lastEntry(accountId).orElse(null);
		String prevHash = last != null ? last.hash() : "0".repeat(64);
		String idem = kind.name().toLowerCase(Locale.ROOT) + ":" + refId;
		String hash = hashOf(prevHash, accountId, amount, kind, refId, idem);
		CoinEntry entry = CoinEntry.create("cent_" + UUID.randomUUID().toString().replace("-", ""), accountId,
			amount, kind, refId, message, idem, prevHash, hash, now);
		CoinEntry saved = ledger.insertEntry(entry);
		long nextBalance = account.balance() + amount;
		CoinAccount updated = ledger.updateBalance(accountId, nextBalance, account.version(), account.version() + 1);
		if (updated == null) {
			throw new ApiException(409, "COIN_CONCURRENCY", "Movimentação concorrente; tente de novo.");
		}
		return saved;
	}

	private String hashOf(String prevHash, String accountId, long amount, CoinEntry.Kind kind, String refId,
			String idem) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			String payload = prevHash + "|" + accountId + "|" + amount + "|" + kind + "|" + refId + "|" + idem;
			return HexFormat.of().formatHex(digest.digest(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		}
		catch (Exception impossible) {
			throw new IllegalStateException("SHA-256 indisponível");
		}
	}

	/** Transferência P2P estilo Pix: por username, instantânea, irreversível. */
	@Transactional
	public CoinEntry transfer(String fromAccountId, String toUsername, long amount, String message, String toUserId,
			String toUsernameResolved) {
		if (amount <= 0) {
			throw new ApiException(400, "INVALID_AMOUNT", "O valor precisa ser maior que zero.");
		}
		if (fromAccountId.equals(toUserId)) {
			throw new ApiException(400, "SELF_TRANSFER", "Não dá para transferir para você mesmo.");
		}
		ensureGenesis(fromAccountId, fromAccountId);
		CoinAccount from = ensureAccount(fromAccountId);
		CoinAccount to = ensureAccount(toUserId);
		// Teto de conta nova: os primeiros 7 dias enviam até 500/dia.
		if (isNewAccount(from)) {
			Instant start = LocalDate.now(clock).atStartOfDay().toInstant(ZoneOffset.UTC);
			long sentToday = ledger.transferredOn(fromAccountId, start, start.plus(Duration.ofDays(1)));
			if (sentToday + amount > NEW_ACCOUNT_DAILY_CAP) {
				throw new ApiException(403, "NEW_ACCOUNT_LIMIT",
					"Contas novas enviam até " + NEW_ACCOUNT_DAILY_CAP + " faíscas por dia nos primeiros 7 dias.");
			}
		}
		if (from.balance() < amount) {
			throw new ApiException(400, "INSUFFICIENT_SPARKS", "Faíscas insuficientes.");
		}
		String transferRef = "p2p_" + UUID.randomUUID().toString().replace("-", "");
		Instant now = clock.instant();
		CoinEntry out = grant(fromAccountId, -amount, CoinEntry.Kind.TRANSFER_OUT, transferRef,
			message == null ? "" : message, now);
		grant(toUserId, amount, CoinEntry.Kind.TRANSFER_IN, transferRef, message == null ? "" : message, now);
		return out;
	}

	private boolean isNewAccount(CoinAccount account) {
		return Duration.between(account.createdAt(), clock.instant()).compareTo(NEW_ACCOUNT_WINDOW) < 0;
	}

	@Transactional(readOnly = true)
	public long balance(String accountId) {
		return ensureAccount(accountId).balance();
	}

	@Transactional(readOnly = true)
	public List<CoinEntry> entries(String accountId, int limit) {
		return ledger.recentEntries(accountId, limit);
	}
}
