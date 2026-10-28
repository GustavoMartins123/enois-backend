package com.socialcoisa.rede.coin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.socialcoisa.rede.coin.application.port.out.CoinLedgerRepository;
import com.socialcoisa.rede.coin.domain.CoinRate;

/**
 * O botão "Recalcular agora" rodava a mesma conta do cron e publicava um passo de UM DIA a cada
 * clique — clicando, o valor despencava como se tivessem passado vários períodos. Agora o passo
 * publicável é proporcional ao tempo decorrido desde a última publicação.
 */
class CoinEconomyServiceTests {

	private static final Instant START = Instant.parse("2026-10-01T03:10:00Z");

	private final AtomicReference<CoinRate> latest =
		new AtomicReference<>(new CoinRate("rate_1", 1, 10_000, null, START.minus(Duration.ofDays(2))));

	private final AtomicReference<Instant> now = new AtomicReference<>(START);

	private final Clock clock = new Clock() {

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now.get();
		}
	};

	@Test
	void cliquesSeguidosNaoValemUmPeriodoCada() {
		CoinEconomyService service = service();

		var first = service.recalculate();
		assertThat(first.published()).isTrue();
		assertThat(first.currentMicros()).isEqualTo(9_900);

		// Cinco cliques em seguida, dentro do mesmo período: nenhum publica de novo.
		for (int click = 0; click < 5; click++) {
			now.set(now.get().plus(Duration.ofMinutes(1)));
			var again = service.recalculate();
			assertThat(again.published()).isFalse();
			assertThat(again.currentMicros()).isEqualTo(9_900);
			assertThat(again.elapsedFraction()).isLessThan(0.01);
		}
	}

	@Test
	void depoisDeUmPeriodoInteiroOPassoVoltaACheio() {
		CoinEconomyService service = service();
		service.recalculate();

		now.set(now.get().plus(Duration.ofHours(24)));
		var next = service.recalculate();

		assertThat(next.elapsedFraction()).isEqualTo(1.0);
		assertThat(next.published()).isTrue();
		assertThat(next.currentMicros()).isEqualTo(9_801);
	}

	@Test
	void painelNaoPrometeUmMovimentoQueOCliqueNaoFaz() {
		CoinEconomyService service = service();
		service.recalculate();

		// Meio período depois, a sugestão do painel é metade do passo (0,5%), não o passo inteiro.
		now.set(now.get().plus(Duration.ofHours(12)));
		var snapshot = service.snapshot();

		assertThat(snapshot.published()).isFalse();
		assertThat(snapshot.currentMicros()).isEqualTo(9_900);
		assertThat(snapshot.elapsedFraction()).isEqualTo(0.5);
		assertThat(snapshot.result().suggestedMicros()).isEqualTo(9_851);
	}

	private CoinEconomyService service() {
		CoinLedgerRepository ledger = mock(CoinLedgerRepository.class);
		when(ledger.sumAmountByKindBetween(any(), any(), any())).thenReturn(0L);

		CoinRateService rates = mock(CoinRateService.class);
		when(rates.latest()).thenAnswer(invocation -> latest.get());
		when(rates.publish(anyLong(), anyString())).thenAnswer(invocation -> {
			CoinRate rate = CoinRate.create(invocation.getArgument(0), invocation.getArgument(1), now.get());
			latest.set(rate);
			return rate;
		});

		CoinService coins = mock(CoinService.class);
		when(coins.supply())
			.thenReturn(new CoinService.SupplyView(2_000_000_000, 1_000_000_000, 1_000_000_000,
				1_000_000_000, true));

		return new CoinEconomyService(ledger, rates, coins, clock, true, 24, 30, 0.05, 0.5, 1.0, 0.5, 0.25,
			0.01, 1_000, 1_000_000);
	}

}