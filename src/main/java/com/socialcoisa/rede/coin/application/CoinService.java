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

import org.springframework.scheduling.annotation.Scheduled;
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
	/** Conta de RETENÇÃO do escrow: não pertence a ninguém; as faíscas ficam paradas aqui. */
	public static final String ESCROW_ACCOUNT = "coin_escrow";
	/** Taxa da plataforma sobre vendas (Fase 4/D1): 10% do preço, ida para a tesouraria. */
	public static final long MARKETPLACE_FEE_PERCENT = 10L;
	/** Volume interno fixo do genesis. Não é público; nunca muda. */
	private static final long TREASURY_GENESIS = 2_000_000L;
	public static final long AIRDROP_PER_USER = 100L;
	public static final long AIRDROP_OWNER = 150_000L;
	/** Presença diária (plano C2): 5/dia, bônus de 25 a cada 7 dias seguidos. */
	public static final long DAILY_PRESENCE = 5L;
	public static final long STREAK_BONUS = 25L;
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

	/** Conta da moeda: cria a conta, executa o airdrop do genesis e marca a presença do dia. */
	@Transactional
	public Wallet wallet(String accountId, String username) {
		CoinAccount account = ensureAccount(accountId);
		ensureGenesis(accountId, username);
		ensureDailyPresence(accountId);
		return new Wallet(ensureAccount(accountId).balance(), username,
			ledger.recentEntries(accountId, 50));
	}

	/**
	 * Presença diária (plano C2): +5 por dia; a cada 7 dias consecutivos de presença,
	 * +25 de bônus. Idempotente por conta+dia — abrir a carteira mil vezes não duplica.
	 */
	private void ensureDailyPresence(String accountId) {
		LocalDate today = LocalDate.now(clock);
		String ref = "presenca:" + accountId + ":" + today;
		if (ledger.existsByKindAndRefId(CoinEntry.Kind.REWARD, ref)) {
			return;
		}
		Instant now = Instant.now(clock);
		// Contagem de presenças na janela de 7 dias: o 7º dia consecutivo ganha o bônus.
		long dias = 1; // hoje conta
		for (int i = 1; i <= 7; i++) {
			String prevRef = "presenca:" + accountId + ":" + today.minusDays(i);
			if (!ledger.existsByKindAndRefId(CoinEntry.Kind.REWARD, prevRef)) {
				break;
			}
			dias++;
		}
		long amount = DAILY_PRESENCE;
		String message = "Presença diária";
		if (dias >= 7) {
			amount += STREAK_BONUS;
			message = "Presença diária (bônus de " + dias + " dias seguidos)";
		}
		ensureTreasury();
		grant(TREASURY_ACCOUNT, -amount, CoinEntry.Kind.GENESIS_TREASURY, "presenca-tesouraria:" + ref,
			message, now);
		grant(accountId, amount, CoinEntry.Kind.REWARD, ref, message, now);
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

	/**
	 * Escrow (marketplace/serviços): as faíscas SAEM do comprador e ficam RETIDAS na conta de
	 * escrow — não gastas, não sacáveis por ninguém até a decisão (liberação ou reembolso).
	 */
	@Transactional
	public CoinEntry escrowHold(String buyerAccountId, long amount, String purchaseRef, String message) {
		if (amount <= 0) {
			throw new ApiException(400, "INVALID_AMOUNT", "O preço precisa ser positivo.");
		}
		Instant now = clock.instant();
		CoinEntry out = grant(buyerAccountId, -amount, CoinEntry.Kind.ESCROW_OUT, purchaseRef,
			message == null ? "" : message, now);
		grant(ESCROW_ACCOUNT, amount, CoinEntry.Kind.ESCROW_HOLD, purchaseRef, message == null ? "" : message, now);
		return out;
	}

	/**
	 * Liberação do escrow: vendedor recebe preço − 10% de taxa; a taxa volta para a
	 * tesouraria (o supply não muda — as faíscas só mudam de conta).
	 */
	@Transactional
	public void escrowRelease(String sellerAccountId, long amount, String purchaseRef, String message) {
		if (amount <= 0) {
			throw new ApiException(400, "INVALID_AMOUNT", "O preço precisa ser positivo.");
		}
		Instant now = clock.instant();
		long fee = amount * MARKETPLACE_FEE_PERCENT / 100;
		long net = amount - fee;
		grant(ESCROW_ACCOUNT, -amount, CoinEntry.Kind.ESCROW_RELEASE, purchaseRef, message == null ? "" : message, now);
		grant(sellerAccountId, net, CoinEntry.Kind.ESCROW_IN, purchaseRef, message == null ? "" : message, now);
		if (fee > 0) {
			ensureTreasury();
			grant(TREASURY_ACCOUNT, fee, CoinEntry.Kind.FEE_IN, purchaseRef, "Taxa de marketplace", now);
		}
	}

	/** Reembolso do escrow: volta INTEGRAL para o comprador. */
	@Transactional
	public void escrowRefund(String buyerAccountId, long amount, String purchaseRef, String message) {
		if (amount <= 0) {
			throw new ApiException(400, "INVALID_AMOUNT", "O preço precisa ser positivo.");
		}
		Instant now = clock.instant();
		grant(ESCROW_ACCOUNT, -amount, CoinEntry.Kind.ESCROW_RELEASE, purchaseRef + ":refund",
			message == null ? "" : message, now);
		grant(buyerAccountId, amount, CoinEntry.Kind.REFUND_IN, purchaseRef + ":refund",
			message == null ? "" : message, now);
	}

	/** Recompensas do mês por prefixo (teto mensal do earned, ex.: microprojetos 3/mês). */
	public long rewardCountByPrefix(String prefix) {
		return ledger.countRewardsByRefPrefix(prefix);
	}

	/** A conta já ganhou essa recompensa? (idempotência legível para os hooks do earned) */
	public boolean hasReward(String ref) {
		return ledger.existsByKindAndRefId(CoinEntry.Kind.REWARD, ref);
	}

	/**
	 * Recompensa de earned (plano C2): sai da tesouraria, entra na conta — supply fixo
	 * preservado. Idempotente por ref (a mesma recompensa nunca duplica).
	 */
	@Transactional
	public void reward(String accountId, long amount, String ref, String message) {
		if (amount <= 0) {
			throw new ApiException(400, "INVALID_AMOUNT", "O valor precisa ser positivo.");
		}
		if (ledger.existsByKindAndRefId(CoinEntry.Kind.REWARD, ref)) {
			return;
		}
		ensureTreasury();
		Instant now = Instant.now(clock);
		grant(TREASURY_ACCOUNT, -amount, CoinEntry.Kind.GENESIS_TREASURY, "reward-tesouraria:" + ref, message, now);
		grant(accountId, amount, CoinEntry.Kind.REWARD, ref, message, now);
	}

	/**
	 * Reconciliação (plano C4): o LEDGER é a verdade — reconstrói o saldo de cada conta
	 * somando os lançamentos e compara com o snapshot. Divergência LOGA ALERTA (nada de
	 * auto-correção silenciosa: a investigação vem primeiro). Roda de madrugada.
	 */
	@Scheduled(cron = "0 17 4 * * *")
	@Transactional(readOnly = true)
	public void reconcile() {
		for (CoinAccount account : ledger.allAccounts()) {
			long rebuilt = ledger.balanceFromLedger(account.accountId());
			if (rebuilt != account.balance()) {
				System.out.println("[FAISCA][RECONCILIACAO] DIVERGENCIA conta=" + account.accountId()
					+ " snapshot=" + account.balance() + " ledger=" + rebuilt);
			}
		}
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
