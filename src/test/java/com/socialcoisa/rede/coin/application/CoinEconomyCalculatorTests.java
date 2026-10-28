package com.socialcoisa.rede.coin.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.socialcoisa.rede.coin.application.CoinEconomyCalculator.Metrics;
import com.socialcoisa.rede.coin.application.CoinEconomyCalculator.Params;

/** A cotação automática: neutra sem uso, sobe com uso, desce com emissão e respeita os limites. */
class CoinEconomyCalculatorTests {

	private final Params params = Params.defaults();

	@Test
	void semUsoNemEmissaoACotacaoNaoMuda() {
		var result = CoinEconomyCalculator.evaluate(10_000, new Metrics(0, 0, 0, 1_000_000), params);

		assertThat(result.suggestedMicros()).isEqualTo(10_000);
	}

	@Test
	void usoAcimaDaMetaSobeUmPassoNoMaximo() {
		// 20% do estoque usado na janela contra meta de 5%: índice 4, mas o passo é limitado a 1%.
		var result = CoinEconomyCalculator.evaluate(10_000, new Metrics(200_000, 0, 0, 1_000_000), params);

		assertThat(result.index()).isGreaterThan(1);
		assertThat(result.suggestedMicros()).isEqualTo(10_100);
	}

	@Test
	void emissaoAltaDiluiEDerrubaUmPasso() {
		// No piso (1 centavo) a cotação não desce mais: o cálculo só sinaliza o índice baixo.
		var atFloor = CoinEconomyCalculator.evaluate(10_000,
			new Metrics(50_000, 0, 1_000_000, 1_000_000), params);
		assertThat(atFloor.index()).isLessThan(1);
		assertThat(atFloor.suggestedMicros()).isEqualTo(10_000);

		// Acima do piso, a emissão derruba 1% no passo.
		var aboveFloor = CoinEconomyCalculator.evaluate(20_000,
			new Metrics(50_000, 0, 1_000_000, 1_000_000), params);
		assertThat(aboveFloor.suggestedMicros()).isEqualTo(19_800);
	}

	@Test
	void poeiraEntreContasNaoEstouraOCalculo() {
		// Uso declarado de 10x o estoque: o teto de uma volta por janela limita o índice.
		var result = CoinEconomyCalculator.evaluate(10_000, new Metrics(10_000_000, 0, 0, 1_000_000), params);

		assertThat(result.effectiveUsage()).isEqualTo(1_000_000);
		assertThat(result.suggestedMicros()).isEqualTo(10_100);
	}

	@Test
	void p2pEntraComPesoMenorQueOConsumo() {
		var p2pOnly = CoinEconomyCalculator.evaluate(10_000, new Metrics(0, 200_000, 0, 1_000_000), params);
		var escrowOnly = CoinEconomyCalculator.evaluate(10_000, new Metrics(200_000, 0, 0, 1_000_000), params);

		assertThat(p2pOnly.index()).isLessThan(escrowOnly.index());
	}

	@Test
	void respeitaPisoETetoDePreco() {
		var floor = CoinEconomyCalculator.evaluate(10_000, new Metrics(0, 0, 10_000_000, 1_000_000), params);
		assertThat(floor.suggestedMicros()).isEqualTo(10_000);

		var ceilingParams = new Params(30, 0.05, 0.5, 1.0, 0.25, 0.01, 10_000, 999_900);
		var ceiling = CoinEconomyCalculator.evaluate(999_900,
			new Metrics(10_000_000, 0, 0, 1_000_000), ceilingParams);
		assertThat(ceiling.suggestedMicros()).isEqualTo(999_900);
	}

	@Test
	void semCirculacaoNaoReavalia() {
		var result = CoinEconomyCalculator.evaluate(12_345, new Metrics(500_000, 0, 0, 0), params);

		assertThat(result.suggestedMicros()).isEqualTo(12_345);
	}
}