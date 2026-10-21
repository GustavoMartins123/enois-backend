package com.socialcoisa.rede.moderation.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Ledger do feedback humano e pesos por sinal — a parte persistida do aprendizado. */
public interface ModerationLearningRepository {

	record Feedback(String id, String reportId, String decision, String source, String decidedBy,
			String signals, Integer score, Instant createdAt) {
	}

	void save(Feedback feedback);

	List<Feedback> recent(int limit);

	Map<String, Integer> weights();

	void upsertWeight(String signal, int weight, int samples, Instant now);
}
