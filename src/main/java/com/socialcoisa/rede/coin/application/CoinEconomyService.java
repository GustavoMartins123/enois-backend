package com.socialcoisa.rede.coin.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.socialcoisa.rede.coin.application.port.out.CoinLedgerRepository;
import com.socialcoisa.rede.coin.domain.CoinEntry;

/**
 * Reavalia a cotação de REFERÊNCIA da faísca a partir do uso real.
 *
 * <p>Fontes (janela configurável, padrão 30 dias): escrow de marketplace/serviços
 * ({@code ESCROW_HOLD}), transferências P2P ({@code TRANSFER_IN}) e emissão automática
 * ({@code REWARD}). A fórmula vive no {@link CoinEconomyCalculator}; aqui só se buscam as
 * métricas no ledger e se publica o resultado no histórico da cotação.</p>
 *
 * <p>Salvaguardas: passo máximo por execução, piso/teto de preço, peso menor para P2P e teto de
 * uso por janela (ping-pong entre contas não infla o preço). Se não houver circulação, nada muda.
 * O valor-base da emissão automática continua sendo o da cotação inicial; a conversão fica no
 * {@link CoinService#scaledReward(long)}.</p>
 */
@Service
public class CoinEconomyService {

	private static final Logger LOG = LoggerFactory.getLogger(CoinEconomyService.class);

	public record Summary(int windowDays, CoinEconomyCalculator.Metrics metrics,
			CoinEconomyCalculator.Result result, long previousMicros, long currentMicros, boolean published) {
	}

	private final CoinLedgerRepository ledger;
	private final CoinRateService rates;
	private final CoinService coins;
	private final Clock clock;
	private final boolean enabled;
	private final CoinEconomyCalculator.Params params;

	public CoinEconomyService(CoinLedgerRepository ledger, CoinRateService rates, CoinService coins, Clock clock,
			@Value("${rede.coin.economy.enabled:true}") boolean enabled,
			@Value("${rede.coin.economy.window-days:30}") int windowDays,
			@Value("${rede.coin.economy.target-turnover:0.05}") double targetTurnover,
			@Value("${rede.coin.economy.p2p-weight:0.5}") double p2pWeight,
			@Value("${rede.coin.economy.max-usage-ratio:1.0}") double maxUsageRatio,
			@Value("${rede.coin.economy.sensitivity:0.25}") double sensitivity,
			@Value("${rede.coin.economy.max-step:0.01}") double maxStep,
			@Value("${rede.coin.economy.min-cents:1}") long minCents,
			@Value("${rede.coin.economy.max-cents:100}") long maxCents) {
		this.ledger = ledger;
		this.rates = rates;
		this.coins = coins;
		this.clock = clock;
		this.enabled = enabled;
		this.params = new CoinEconomyCalculator.Params(windowDays, targetTurnover, p2pWeight, maxUsageRatio,
			sensitivity, maxStep, Math.max(1, minCents) * com.socialcoisa.rede.coin.domain.CoinRate.MICROS_PER_CENT,
			Math.max(minCents, maxCents) * com.socialcoisa.rede.coin.domain.CoinRate.MICROS_PER_CENT);
	}

	/** Execução diária: publica uma nova cotação quando o valor sugerido mudou. */
	@Scheduled(cron = "${rede.coin.economy.cron:0 10 3 * * *}",
		zone = "${rede.coin.economy.zone:America/Sao_Paulo}")
	@Transactional
	public void scheduledRecalculate() {
		if (!enabled) {
			return;
		}
		try {
			Summary summary = recalculate();
			if (summary.published()) {
				LOG.info("coin_economy quote_micros={} previous={} usage={} issuance={} circulation={}",
					summary.result().suggestedMicros(), summary.previousMicros(),
					summary.metrics().escrowUsage() + summary.metrics().p2pUsage(),
					summary.metrics().issuance(), summary.metrics().circulation());
			}
		}
		catch (RuntimeException failure) {
			LOG.warn("coin_economy_failed error={}", failure.getMessage());
		}
	}

	/** Calcula e, se o botão estiver ligado, publica a cotação sugerida. */
	@Transactional
	public Summary recalculate() {
		long previous = rates.latest().brlMicros();
		CoinEconomyCalculator.Metrics metrics = metrics();
		CoinEconomyCalculator.Result result = CoinEconomyCalculator.evaluate(previous, metrics, params);
		boolean published = false;
		if (enabled && result.suggestedMicros() != previous) {
			rates.publish(result.suggestedMicros(), note(metrics, result));
			published = true;
		}
		return new Summary(params.windowDays(), metrics, result, previous,
			published ? result.suggestedMicros() : previous, published);
	}

	/** Leitura para o painel: as mesmas métricas, sem publicar nada. */
	@Transactional
	public Summary snapshot() {
		long previous = rates.latest().brlMicros();
		CoinEconomyCalculator.Metrics metrics = metrics();
		return new Summary(params.windowDays(), metrics, CoinEconomyCalculator.evaluate(previous, metrics, params),
			previous, previous, false);
	}

	private CoinEconomyCalculator.Metrics metrics() {
		Instant to = clock.instant();
		Instant from = to.minus(Duration.ofDays(params.windowDays()));
		long escrow = ledger.sumAmountByKindBetween(CoinEntry.Kind.ESCROW_HOLD, from, to);
		long p2p = ledger.sumAmountByKindBetween(CoinEntry.Kind.TRANSFER_IN, from, to);
		long issuance = ledger.sumAmountByKindBetween(CoinEntry.Kind.REWARD, from, to);
		long circulation = coins.supply().circulacao();
		return new CoinEconomyCalculator.Metrics(escrow, p2p, issuance, circulation);
	}

	private String note(CoinEconomyCalculator.Metrics metrics, CoinEconomyCalculator.Result result) {
		return "Reavaliação automática por uso: uso " + faiscas(metrics.escrowUsage() + metrics.p2pUsage())
			+ ", emissão " + faiscas(metrics.issuance()) + ", giro " + faiscas(metrics.circulation())
			+ ", índice " + String.format(java.util.Locale.ROOT, "%.2f", result.index());
	}

	private static String faiscas(long mf) {
		return java.math.BigDecimal.valueOf(mf, 3).stripTrailingZeros().toPlainString();
	}
}