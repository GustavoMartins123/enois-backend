package com.socialcoisa.rede.moderation.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.socialcoisa.rede.moderation.application.port.out.ModerationLearningRepository;
import com.socialcoisa.rede.moderation.domain.Report;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Aprendizado simples e auditável a partir da revisão humana.
 *
 * Cada decisão vira uma linha em `moderation_feedback` com os sinais que a pré-triagem viu.
 * Periodicamente, para cada sinal, o peso sobe quando as denúncias com aquele sinal costumam
 * terminar em punição e desce quando costumam ser descartadas. Não é um modelo caixa-preta:
 * são pesos por sinal, versionáveis e limitados, usados pela pré-triagem seguinte.
 */
@Service
public class ModerationLearningService {

	private static final Logger LOG = LoggerFactory.getLogger(ModerationLearningService.class);
	private static final int SAMPLE_WINDOW = 5_000;
	private static final int MIN_SAMPLES = 5;

	private final ModerationLearningRepository repository;
	private final ObjectMapper objectMapper;
	private final Clock clock;

	public ModerationLearningService(ModerationLearningRepository repository, ObjectMapper objectMapper, Clock clock) {
		this.repository = repository;
		this.objectMapper = objectMapper;
		this.clock = clock;
	}

	@Transactional
	public void record(Report report, String decision, String source, String decidedBy) {
		String signals = signalsCsv(report.preTriage());
		if (signals == null && report.preTriageScore() == null) {
			return; // denúncia anterior à pré-triagem: não há feature para aprender
		}
		repository.save(new ModerationLearningRepository.Feedback(
			"mfb_" + UUID.randomUUID().toString().replace("-", ""), report.id(), decision, source, decidedBy,
			signals, report.preTriageScore(), clock.instant()));
	}

	@Scheduled(fixedDelay = 600_000, initialDelay = 60_000)
	@Transactional
	public void learnWeights() {
		List<ModerationLearningRepository.Feedback> feedback = repository.recent(SAMPLE_WINDOW);
		if (feedback.isEmpty()) {
			return;
		}
		Map<String, int[]> counts = new HashMap<>(); // [punish, dismiss]
		for (var item : feedback) {
			if (item.signals() == null || item.signals().isBlank()) {
				continue;
			}
			boolean punish = switch (item.decision()) {
				case "PUNISH", "BAN", "APPROVE", "AUTO_PENALIZE" -> true;
				case "DISMISS" -> false;
				default -> false; // WARN não é punição nem absolvição para o aprendizado
			};
			boolean dismiss = "DISMISS".equals(item.decision());
			if (!punish && !dismiss) {
				continue;
			}
			for (String signal : item.signals().split(",")) {
				if (signal.isBlank()) {
					continue;
				}
				int[] pair = counts.computeIfAbsent(signal.trim(), ignored -> new int[2]);
				if (punish) pair[0]++;
				if (dismiss) pair[1]++;
			}
		}
		Instant now = clock.instant();
		for (var entry : counts.entrySet()) {
			int punish = entry.getValue()[0];
			int dismiss = entry.getValue()[1];
			int total = punish + dismiss;
			int base = ModerationSignalCatalog.BASE_WEIGHTS.getOrDefault(entry.getKey(), 0);
			if (total < MIN_SAMPLES || base == 0) {
				continue;
			}
			double punishedRate = punish / (double) total;
			int learned = (int) Math.round(base + 30 * (punishedRate - 0.5));
			int clamped = Math.max(1, Math.min(100, learned));
			repository.upsertWeight(entry.getKey(), clamped, total, now);
		}
		LOG.info("Pesos de moderação recalculados sobre {} feedbacks.", feedback.size());
	}

	private String signalsCsv(String preTriageJson) {
		if (preTriageJson == null || preTriageJson.isBlank()) {
			return null;
		}
		try {
			JsonNode root = objectMapper.readTree(preTriageJson);
			List<String> signals = new ArrayList<>();
			for (JsonNode signal : root.path("signals")) {
				signals.add(signal.asString());
			}
			return signals.isEmpty() ? null : String.join(",", signals);
		}
		catch (Exception invalid) {
			return null;
		}
	}
}
