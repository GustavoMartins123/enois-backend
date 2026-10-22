package com.socialcoisa.rede.groups;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.socialcoisa.rede.groups.domain.Group;
import com.socialcoisa.rede.groups.domain.GroupJoinMode;
import com.socialcoisa.rede.groups.domain.GroupSlug;
import com.socialcoisa.rede.groups.domain.GroupStatus;

/**
 * O ciclo de vida do grupo: o que cada estado permite, como se entra e como o endereço nasce.
 *
 * O que este teste tranca:
 *
 * - ACTIVE é o normal; RESTRICTED para de entrar gente mas o conteúdo segue; SUSPENDED congela
 *   entrada e conteúdo sem apagar o que existe; CLOSED encerra e some da descoberta;
 * - grupo nasce ATIVO, e suspender marca a data (sair de suspenso limpa);
 * - a edição é a do PATCH do resto da plataforma: campo ausente mantém, cabeçalho vazio limpa;
 * - o endereço não perde acento nem vira vazio, e respeita o teto da coluna;
 * - passar a propriedade troca quem responde pelo grupo.
 */
class GroupDomainTests {

	private static final Instant AGORA = Instant.parse("2026-10-18T12:00:00Z");

	@Test
	void cadaEstadoDizOQuePermite() {
		assertThat(GroupStatus.ACTIVE.acceptsNewMembers()).isTrue();
		assertThat(GroupStatus.ACTIVE.acceptsNewContent()).isTrue();
		assertThat(GroupStatus.ACTIVE.visibleInDiscovery()).isTrue();
		assertThat(GroupStatus.ACTIVE.readableByMembers()).isTrue();

		assertThat(GroupStatus.RESTRICTED.acceptsNewMembers())
			.as("restrito para de entrar gente")
			.isFalse();
		assertThat(GroupStatus.RESTRICTED.acceptsNewContent())
			.as("mas o conteúdo segue")
			.isTrue();

		assertThat(GroupStatus.SUSPENDED.acceptsNewMembers()).isFalse();
		assertThat(GroupStatus.SUSPENDED.acceptsNewContent()).isFalse();
		assertThat(GroupStatus.SUSPENDED.visibleInDiscovery()).isFalse();
		assertThat(GroupStatus.SUSPENDED.readableByMembers())
			.as("suspenso congela, não apaga: quem era membro continua lendo")
			.isTrue();

		assertThat(GroupStatus.CLOSED.acceptsNewMembers()).isFalse();
		assertThat(GroupStatus.CLOSED.acceptsNewContent()).isFalse();
		assertThat(GroupStatus.CLOSED.visibleInDiscovery()).isFalse();
		assertThat(GroupStatus.CLOSED.readableByMembers()).isFalse();
	}

	@Test
	void grupoNasceAtivoESemCabecalhoHtml() {
		Group group = Group.create("group_1", "turma-do-cafe", "Turma do Café", "Gente do café", "TECNOLOGIA",
			"avatar.png", "capa.png", GroupJoinMode.APPROVAL, "user_dono", AGORA);

		assertThat(group.status()).isEqualTo(GroupStatus.ACTIVE);
		assertThat(group.bannerHtml()).isNull();
		assertThat(group.joinMode()).isEqualTo(GroupJoinMode.APPROVAL);
		assertThat(group.joinMode().requiresApproval()).isTrue();
		assertThat(group.suspendedAt()).isNull();
		assertThat(group.isOwnedBy("user_dono")).isTrue();
		assertThat(group.isOwnedBy("user_outro")).isFalse();
		assertThat(group.createdAt()).isEqualTo(AGORA);
		assertThat(group.updatedAt()).isEqualTo(AGORA);
	}

	@Test
	void entradaAbertaNaoPedeAprovacao() {
		assertThat(GroupJoinMode.OPEN.requiresApproval()).isFalse();
	}

	@Test
	void aEdicaoMantemOQueNaoVeioELimpaOQueVeioVazio() {
		Group group = Group.create("group_1", "turma", "Turma", "descrição", "TECNOLOGIA", "avatar.png", "capa.png",
			GroupJoinMode.OPEN, "user_dono", AGORA);

		Group semCabecalho = group.update(null, null, null, null, null, "   ", null, AGORA.plusSeconds(60));
		assertThat(semCabecalho.name()).as("campo ausente mantém").isEqualTo("Turma");
		assertThat(semCabecalho.description()).isEqualTo("descrição");
		assertThat(semCabecalho.joinMode()).isEqualTo(GroupJoinMode.OPEN);
		assertThat(semCabecalho.bannerHtml()).as("vazio limpa").isEmpty();
		assertThat(semCabecalho.updatedAt()).isEqualTo(AGORA.plusSeconds(60));

		Group comTudo = group.update("Outro nome", "outra", "JOGOS", "novo-avatar.png", "nova-capa.png",
			"<div>oi</div>", GroupJoinMode.APPROVAL, AGORA.plusSeconds(120));
		assertThat(comTudo.name()).isEqualTo("Outro nome");
		assertThat(comTudo.topic()).isEqualTo("JOGOS");
		assertThat(comTudo.avatar()).isEqualTo("novo-avatar.png");
		assertThat(comTudo.bannerHtml()).isEqualTo("<div>oi</div>");
		assertThat(comTudo.joinMode()).isEqualTo(GroupJoinMode.APPROVAL);
		assertThat(comTudo.slug()).as("o endereço não muda depois de criado").isEqualTo("turma");
	}

	@Test
	void suspenderMarcaADataEVoltarLimpa() {
		Group group = Group.create("group_1", "turma", "Turma", null, null, null, null, GroupJoinMode.OPEN, "user_dono",
			AGORA);

		Group suspenso = group.withStatus(GroupStatus.SUSPENDED, AGORA.plusSeconds(3600));
		assertThat(suspenso.status()).isEqualTo(GroupStatus.SUSPENDED);
		assertThat(suspenso.suspendedAt()).isEqualTo(AGORA.plusSeconds(3600));
		assertThat(suspenso.createdAt()).as("o histórico não se perde").isEqualTo(AGORA);

		Group reativado = suspenso.withStatus(GroupStatus.ACTIVE, AGORA.plusSeconds(7200));
		assertThat(reativado.status()).isEqualTo(GroupStatus.ACTIVE);
		assertThat(reativado.suspendedAt()).isNull();
	}

	@Test
	void passarAPropriedadeTrocaQuemResponde() {
		Group group = Group.create("group_1", "turma", "Turma", null, null, null, null, GroupJoinMode.OPEN, "user_dono",
			AGORA);

		Group doNovoDono = group.withOwner("user_novo", AGORA.plusSeconds(60));
		assertThat(doNovoDono.ownerId()).isEqualTo("user_novo");
		assertThat(doNovoDono.isOwnedBy("user_novo")).isTrue();
		assertThat(doNovoDono.isOwnedBy("user_dono")).isFalse();
		assertThat(doNovoDono.slug()).isEqualTo("turma");
	}

	@Test
	void oEnderecoNaoPerdeAcentoNemViraVazio() {
		assertThat(GroupSlug.from("Café & Cia")).isEqualTo("cafe-cia");
		assertThat(GroupSlug.from("Programação Funcional")).isEqualTo("programacao-funcional");
		assertThat(GroupSlug.from("  Espaços   e Tabulações  ")).isEqualTo("espacos-e-tabulacoes");
		assertThat(GroupSlug.from("!!!")).as("nome só de símbolos não vira endereço vazio").isEqualTo("grupo");
		assertThat(GroupSlug.from(null)).isEqualTo("grupo");
		assertThat(GroupSlug.from("A".repeat(200)))
			.as("respeita o teto da coluna sem deixar hífen pendurado")
			.hasSize(80);
	}
}
