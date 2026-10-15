package com.socialcoisa.rede.shared.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.socialcoisa.rede.shared.search.BooleanQuery.And;
import com.socialcoisa.rede.shared.search.BooleanQuery.MatchAll;
import com.socialcoisa.rede.shared.search.BooleanQuery.Not;
import com.socialcoisa.rede.shared.search.BooleanQuery.Or;
import com.socialcoisa.rede.shared.search.BooleanQuery.Phrase;
import com.socialcoisa.rede.shared.search.BooleanQuery.Term;

class BooleanQueryTests {

	@Test
	void emptyOrBlankQueryMatchesEverything() {
		assertThat(BooleanQuery.parse(null).root()).isInstanceOf(MatchAll.class);
		assertThat(BooleanQuery.parse("   ").root()).isInstanceOf(MatchAll.class);
		assertThat(BooleanQuery.parse("").isEmpty()).isTrue();
	}

	@Test
	void consecutiveTermsMeanAnd() {
		BooleanQuery query = BooleanQuery.parse("java spring");

		assertThat(query.root()).isEqualTo(new And(List.of(new Term("java"), new Term("spring"))));
	}

	@Test
	void explicitAndAndOrRespectPrecedence() {
		// AND liga mais forte que OR: java E spring sao um bloco, e kotlin e a alternativa.
		BooleanQuery query = BooleanQuery.parse("java AND spring OR kotlin");

		assertThat(query.root()).isEqualTo(new Or(List.of(
			new And(List.of(new Term("java"), new Term("spring"))),
			new Term("kotlin"))));
	}

	@Test
	void acceptsPortugueseOperators() {
		assertThat(BooleanQuery.parse("java E spring").root())
			.isEqualTo(new And(List.of(new Term("java"), new Term("spring"))));
		assertThat(BooleanQuery.parse("java OU rust").root())
			.isEqualTo(new Or(List.of(new Term("java"), new Term("rust"))));
		assertThat(BooleanQuery.parse("java NAO estagio").root())
			.isEqualTo(new And(List.of(new Term("java"), new Not(new Term("estagio")))));
	}

	@Test
	void minusExcludesAndPlusRequires() {
		assertThat(BooleanQuery.parse("java -estagio").root())
			.isEqualTo(new And(List.of(new Term("java"), new Not(new Term("estagio")))));
		// O "+" apenas reforca o AND que ja e implicito.
		assertThat(BooleanQuery.parse("+java +spring").root())
			.isEqualTo(new And(List.of(new Term("java"), new Term("spring"))));
	}

	@Test
	void quotedTextBecomesAPhrase() {
		BooleanQuery query = BooleanQuery.parse("\"desenvolvedor back-end\" senior");

		assertThat(query.root()).isEqualTo(new And(List.of(
			new Phrase("desenvolvedor back-end"),
			new Term("senior"))));
	}

	@Test
	void parenthesesGroupExpressions() {
		BooleanQuery query = BooleanQuery.parse("(java OR kotlin) -junior");

		assertThat(query.root()).isEqualTo(new And(List.of(
			new Or(List.of(new Term("java"), new Term("kotlin"))),
			new Not(new Term("junior")))));
	}

	@Test
	void notAppliesToTheWholeGroup() {
		BooleanQuery query = BooleanQuery.parse("NOT (java OR kotlin)");

		assertThat(query.root()).isEqualTo(new Not(new Or(List.of(new Term("java"), new Term("kotlin")))));
	}

	@Test
	void normalizesCaseAndSpacing() {
		BooleanQuery query = BooleanQuery.parse("  JAVA    \"Frase   Exata\"  ");

		assertThat(query.root()).isEqualTo(new And(List.of(new Term("java"), new Phrase("frase exata"))));
	}

	@Test
	void toleratesUnbalancedInputInsteadOfFailing() {
		// Quem digita uma busca nao deveria receber erro de sintaxe.
		assertThat(BooleanQuery.parse("\"sem fim").root()).isEqualTo(new Phrase("sem fim"));
		assertThat(BooleanQuery.parse("(java AND spring").root())
			.isEqualTo(new And(List.of(new Term("java"), new Term("spring"))));
		assertThat(BooleanQuery.parse("java OR").root()).isEqualTo(new Term("java"));
		assertThat(BooleanQuery.parse(") java (").root()).isEqualTo(new Term("java"));
		assertThat(BooleanQuery.parse("AND java").root()).isEqualTo(new Term("java"));
		assertThat(BooleanQuery.parse("-").root()).isInstanceOf(MatchAll.class);
	}

	@Test
	void refusesQueriesThatWouldExplodeTheWhereClause() {
		String manyTerms = String.join(" ", java.util.Collections.nCopies(70, "termo"));
		assertThatThrownBy(() -> BooleanQuery.parse(manyTerms))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("termos");

		String deeplyNested = "(".repeat(20) + "java" + ")".repeat(20);
		assertThatThrownBy(() -> BooleanQuery.parse(deeplyNested))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("parenteses");
	}
}