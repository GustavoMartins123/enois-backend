package com.socialcoisa.rede.shared.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Defesa contra texto patologico ("travazap").
 *
 * O ponto destes testes e o outro lado: alem de neutralizar o que trava, o texto de verdade tem de
 * passar inteiro — inclusive arabe e hebraico, que sao justamente o alfabeto do ataque.
 */
class PlainTextTests {

	@Test
	void removesBidiAndZeroWidthControls() {
		String ataque = "oi\u202E\u202D\u202C\u200F\u200E\u2066\u2069\u200B\u200D\uFEFF tudo bem";

		assertThat(PlainText.defuse(ataque)).isEqualTo("oi tudo bem");
	}

	@Test
	void keepsRealArabicAndHebrewText() {
		String arabe = "مرحبا بالعالم";
		String hebraico = "שלום עולם";

		assertThat(PlainText.defuse(arabe)).isEqualTo(arabe);
		assertThat(PlainText.defuse(hebraico)).isEqualTo(hebraico);
	}

	@Test
	void keepsEmojiAndAccentsIntact() {
		String normal = "Olá! 🎉 programação, café e ação";

		assertThat(PlainText.defuse(normal)).isEqualTo(normal);
	}

	@Test
	void limitsPilesOfCombiningMarksButKeepsTheBase() {
		String pilha = "e" + "\u0301".repeat(40);

		String resultado = PlainText.defuse(pilha);

		assertThat(resultado).startsWith("e");
		assertThat(resultado.length()).isLessThanOrEqualTo(1 + 5);
	}

	@Test
	void collapsesHugeRepetitions() {
		String flood = "a".repeat(5000) + " fim";

		String resultado = PlainText.defuse(flood);

		assertThat(resultado).startsWith("a".repeat(12)).doesNotContain("a".repeat(13));
		assertThat(resultado).endsWith(" fim");
	}

	@Test
	void emptyAndNullStayAsTheyAre() {
		assertThat(PlainText.defuse(null)).isNull();
		assertThat(PlainText.defuse("")).isEmpty();
		assertThat(PlainText.defuse("   ")).isEqualTo("   ");
	}
}
