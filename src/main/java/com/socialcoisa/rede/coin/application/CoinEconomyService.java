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

	/**
	 * @param elapsedFraction quanto do período já passou desde a última publicação (1.0 = período
	 * completo); o passo máximo publicável é proporcional a ele
	 * @param nextEvaluationAt quando a próxima publicação pode acontecer
	 */
	public record Summary(int windowDays, CoinEconomyCalculator.Metrics metrics,
			CoinEconomyCalculator.Result result, long previousMicros, long currentMicros, boolean published,
			double elapsedFraction, Instant nextEvaluationAt) {
	}

	private final CoinLedgerRepository ledger;
	private final CoinRateService rates;
	private final CoinService coins;
	private final Clock clock;
	private final boolean enabled;
	private final CoinEconomyCalculator.Params params;
	/** Período de referência de uma reavaliação completa (o cron é diário). */
	private final Duration period;

	public CoinEconomyService(CoinLedgerRepository ledger, CoinRateService rates, CoinService coins, Clock clock,
			@Value("${rede.coin.economy.enabled:true}") boolean enabled,
			@Value("${rede.coin.economy.period-hours:24}") int periodHours,
			@Value("${rede.coin.economy.window-days:30}") int windowDays,
			@Value("${rede.coin.economy.target-turnover:0.05}") double targetTurnover,
			@Value("${rede.coin.economy.p2p-weight:0.5}") double p2pWeight,
			@Value("${rede.coin.economy.max-usage-ratio:1.0}") double maxUsageRatio,
			@Value("${rede.coin.economy.scarcity-weight:0.5}") double scarcityWeight,
			@Value("${rede.coin.economy.sensitivity:0.25}") double sensitivity,
			@Value("${rede.coin.economy.max-step:0.01}") double maxStep,
			@Value("${rede.coin.economy.min-micros:1000}") long minMicros,
			@Value("${rede.coin.economy.max-micros:1000000}") long maxMicros) {
		this.ledger = ledger;
		this.rates = rates;
		this.coins = coins;
		this.clock = clock;
		this.enabled = enabled;
		this.period = Duration.ofHours(Math.max(1, periodHours));
		this.params = new CoinEconomyCalculator.Params(windowDays, targetTurnover, p2pWeight, maxUsageRatio,
			scarcityWeight, sensitivity, maxStep, minMicros, maxMicros);
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

	/**
	 * Calcula e, se o botão estiver ligado, publica a cotação sugerida. O passo publicável é
	 * proporcional ao tempo decorrido desde a última publicação: o cron diário dá o passo cheio,
	 * e cliques manuais seguidos não valem um período cada um.
	 */
	@Transactional
	public Summary recalculate() {
		var latest = rates.latest();
		long previous = latest.brlMicros();
		Instant lastAt = latest.effectiveAt();
		Instant now = clock.instant();
		double elapsed = elapsedFraction(lastAt, now);
		CoinEconomyCalculator.Metrics metrics = metrics();
		CoinEconomyCalculator.Result result = CoinEconomyCalculator.evaluate(previous, metrics, params, elapsed);
		boolean published = false;
		if (enabled && result.suggestedMicros() != previous) {
			rates.publish(result.suggestedMicros(), note(metrics, result));
			published = true;
		}
		Instant next = published || lastAt == null ? now.plus(period) : lastAt.plus(period);
		return new Summary(params.windowDays(), metrics, result, previous,
			published ? result.suggestedMicros() : previous, published, elapsed, next);
	}

	/**
	 * Leitura para o painel: as mesmas métricas, sem publicar nada. A sugestão é a que um
	 * "recalcular agora" publicaria — proporcional ao período já decorrido, para o painel não
	 * prometer um movimento que o clique não faz.
	 */
	@Transactional
	public Summary snapshot() {
		var latest = rates.latest();
		long previous = latest.brlMicros();
		Instant now = clock.instant();
		Instant lastAt = latest.effectiveAt();
		double elapsed = elapsedFraction(lastAt, now);
		CoinEconomyCalculator.Metrics metrics = metrics();
		Instant next = lastAt == null ? now : lastAt.plus(period);
		return new Summary(params.windowDays(), metrics,
			CoinEconomyCalculator.evaluate(previous, metrics, params, elapsed), previous, previous, false, elapsed,
			next);
	}

	/** Fração do período já decorrida desde a última publicação, limitada a [0, 1]. */
	double elapsedFraction(Instant lastPublishedAt, Instant now) {
		if (lastPublishedAt == null) {
			return 1.0;
		}
		long seconds = Duration.between(lastPublishedAt, now).getSeconds();
		if (seconds <= 0) {
			return 0.0;
		}
		return Math.min(1.0, seconds / (double) period.toSeconds());
	}

	private CoinEconomyCalculator.Metrics metrics() {
		Instant to = clock.instant();
		Instant from = to.minus(Duration.ofDays(params.windowDays()));
		long escrow = ledger.sumAmountByKindBetween(CoinEntry.Kind.ESCROW_HOLD, from, to);
		long p2p = ledger.sumAmountByKindBetween(CoinEntry.Kind.TRANSFER_IN, from, to);
		long issuance = ledger.sumAmountByKindBetween(CoinEntry.Kind.REWARD, from, to);
		// Retorno ao cofre: compras da loja (PURCHASE sai do comprador e volta para a tesouraria) e
		// taxas de marketplace (FEE_IN). Sem esse retorno a tesouraria seca e a economia trava.
		long store = Math.max(0, -ledger.sumAmountByKindBetween(CoinEntry.Kind.PURCHASE, from, to));
		long fees = Math.max(0, ledger.sumAmountByKindBetween(CoinEntry.Kind.FEE_IN, from, to));
		long inflow = saturatingAdd(store, fees);
		var supply = coins.supply();
		return new CoinEconomyCalculator.Metrics(escrow, p2p, inflow, issuance, supply.circulacao(),
			supply.cofre());
	}

	private String note(CoinEconomyCalculator.Metrics metrics, CoinEconomyCalculator.Result result) {
		return "Reavaliação automática por uso: giro " + faiscas(metrics.escrowUsage() + metrics.p2pUsage()
			+ metrics.treasuryInflow()) + ", retorno ao cofre " + faiscas(metrics.treasuryInflow())
			+ ", emissão " + faiscas(metrics.issuance()) + ", cofre " + faiscas(metrics.treasuryBalance())
			+ ", índice " + String.format(java.util.Locale.ROOT, "%.2f", result.index());
	}

	private static long saturatingAdd(long a, long b) {
		long sum = a + b;
		return sum < 0 ? Long.MAX_VALUE : sum;
	}

	private static String faiscas(long mf) {
		return java.math.BigDecimal.valueOf(mf, 3).stripTrailingZeros().toPlainString();
	}
}