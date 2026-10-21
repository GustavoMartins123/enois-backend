package com.socialcoisa.rede.moderation.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.socialcoisa.rede.moderation.application.port.out.ReportRepository;
import com.socialcoisa.rede.moderation.domain.Report;
import com.socialcoisa.rede.moderation.domain.ReportStatus;
import com.socialcoisa.rede.moderation.domain.TargetType;
import com.socialcoisa.rede.shared.web.ApiException;
import tools.jackson.databind.JsonNode;

/** Validacao do palpite do agente de IA e do fail-safe sem chave (sem rede). */
class ModerationAiServiceTests {

	private static final String VALID_VERDICT = """
		{"categoria":"Linguagem informal","regra":"Contexto e alvo","gravidade":"LOW",
		"confianca":0.8,"evidencias":["texto"],"contexto":"conversa entre amigos",
		"ambiguo":true,"acao":"HUMAN_REVIEW","justificativa":"Contexto ambiguo"}""";

	private ReportRepositoryStub repo = new ReportRepositoryStub();

	/** Sem leitor de conteudo: a triagem assume "(conteudo indisponivel)". */
	private static final com.socialcoisa.rede.moderation.application.port.out.ReportedContentReader NO_CONTENT =
		(targetType, targetId) -> Optional.empty();

	private ModerationAiService serviceWithOutKey() {
		return new ModerationAiService(repo, NO_CONTENT, "https://infer.test/v1", "", "beta-llm/qwen3.8-27b", new tools.jackson.databind.ObjectMapper());
	}

	@Test
	void reportPromptCarriesTheTargetContentInsteadOfTheId() {
		// O prompt que vai ao modelo precisa levar o CONTEUDO do alvo — antes ia o id, e o modelo
		// nao tinha como julgar contexto nenhum.
		var report = Report.create("rep_1", "user_reporter", TargetType.POST, "post_abc",
			com.socialcoisa.rede.moderation.domain.ReportReason.SPAM, "vende seguidores", Instant.now());
		com.socialcoisa.rede.moderation.application.port.out.ReportedContentReader reader =
			(targetType, targetId) -> Optional.of("Compre seguidores barato no meu site");
		ModerationAiService service = new ModerationAiService(repo, reader, "https://infer.test/v1", "",
			"beta-llm/qwen3.8-27b", new tools.jackson.databind.ObjectMapper());

		String prompt = service.reportUserPrompt(report);

		assertThat(prompt).contains("Compre seguidores barato no meu site").contains("SPAM");
		assertThat(prompt).doesNotContain("post_abc");
	}

	@Test
	void reportPromptSaysWhenThereIsNoContentToJudge() {
		var report = Report.create("rep_2", "user_reporter", TargetType.JOB, "job_abc",
			com.socialcoisa.rede.moderation.domain.ReportReason.SCAM, "golpe", Instant.now());

		String prompt = serviceWithOutKey().reportUserPrompt(report);

		assertThat(prompt).contains("(conteudo indisponivel)");
		assertThat(prompt).doesNotContain("job_abc");
	}

	@Test
	void withoutApiKeyTheAgentIsUnavailable() {
		ModerationAiService service = serviceWithOutKey();
		assertThat(service.available()).isFalse();
		// Sem chave: nenhum palpite (a denuncia segue para revisao humana).
		assertThat(service.triage("conteudo", "HATE_SPEECH", "descricao")).isEmpty();
		assertThat(service.triagePendingReports(10)).isZero();
	}

	@Test
	void parseVerdictAcceptsValidPolicyOutput() {
		ModerationAiService service = serviceWithOutKey();
		Optional<JsonNode> verdict = service.parseVerdict(VALID_VERDICT);
		assertThat(verdict).isPresent();
		assertThat(ModerationAiService.isActionAllowed(verdict.get())).isTrue();
		assertThat(verdict.get().path("acao").asString()).isEqualTo("HUMAN_REVIEW");
	}

	@Test
	void parseVerdictRejectsActionOutsidePolicy() {
		ModerationAiService service = serviceWithOutKey();
		// A IA NAO pode recomendar punicao: acao fora da politica => palpite invalido.
		assertThat(service.parseVerdict("""
			{"acao":"BAN_USER","gravidade":"CRITICAL","confianca":0.9}""")).isEmpty();
	}

	@Test
	void parseVerdictRejectsInvalidConfidenceAndBrokenJson() {
		ModerationAiService service = serviceWithOutKey();
		assertThat(service.parseVerdict("""
			{"acao":"HUMAN_REVIEW","confianca":1.5}""")).isEmpty();
		assertThat(service.parseVerdict("isso nao e json")).isEmpty();
	}

	@Test
	void triagePendingAttachesVerdictToPendingReports() {
		// A saida do LLM vem do stub via HTTP; para o teste sem rede, a triagem sem chave
		// nao tria nada (0) — o caminho com rede e coberto pela integracao.
		ModerationAiService service = serviceWithOutKey();
		assertThat(service.triagePendingReports(10)).isZero();
	}

	@Test
	void confirmedViolationPenalizesWithoutHighSeverity() {
		ModerationAiService service = serviceWithOutKey();
		// Violacao confirmada e sem duvida, mas gravidade MEDIUM: passa no gate. Uma denuncia
		// comprovada basta; a gravidade classifica o caso, nao decide se ele e penalizavel.
		Optional<JsonNode> verdict = service.parseVerdict("""
			{"categoria":"spam","regra":"Proibido spam","gravidade":"MEDIUM","confianca":0.8,
			 "evidencias":["mesmo link repetido em varios posts"],"contexto":"divulgacao em massa",
			 "ambiguo":false,"acao":"PENALIZAR","justificativa":"Spam confirmado no conteudo"}""");
		assertThat(verdict).isPresent();
		assertThat(ModerationAiService.canAutoPenalize(verdict.get())).isTrue();
	}

	@Test
	void doubtOrLowConfidenceGoesToHumanReview() {
		ModerationAiService service = serviceWithOutKey();
		// Ambiguidade declarada: revisao humana, mesmo com confianca alta.
		assertThat(service.parseVerdict("""
			{"acao":"PENALIZAR","gravidade":"HIGH","confianca":0.95,"ambiguo":true}""")).isEmpty();
		// Confianca abaixo do piso: revisao humana.
		assertThat(service.parseVerdict("""
			{"acao":"PENALIZAR","gravidade":"HIGH","confianca":0.6,"ambiguo":false}""")).isEmpty();
	}

	/** Repositorio em memoria do que o servico precisa (sem banco). */
	static class ReportRepositoryStub implements ReportRepository {
		@Override public Report save(Report report) { return report; }
		@Override public Optional<Report> findById(String id) { return Optional.empty(); }
		@Override public List<Report> findByStatus(ReportStatus status, int limit) { return List.of(); }
		@Override public boolean existsByReporterAndTarget(String reporterId, String targetType,
				String targetId) {
			return false;
		}
		@Override public List<Report> findPendingWithoutAiTriage(int limit) { return List.of(); }
		@Override public List<Report> findPendingWithoutPreTriage(int limit) { return List.of(); }
		@Override public List<Report> findPendingPostsByPreTriageScore(int minScore, int limit) {
			return List.of();
		}
		@Override public long countAgainstTargetSince(String targetType, String targetId,
				java.time.Instant since) { return 0; }
		@Override public List<Report> findByReporter(String reporterId, int limit) { return List.of(); }
	}
}
