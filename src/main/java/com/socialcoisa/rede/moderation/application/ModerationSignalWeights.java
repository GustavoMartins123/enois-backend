package com.socialcoisa.rede.moderation.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.socialcoisa.rede.moderation.application.port.out.ModerationLearningRepository;

/** Cache curto dos pesos aprendidos; pre-triagem e automod não consultam o banco a cada denúncia. */
@Component
public class ModerationSignalWeights {

	private final ModerationLearningRepository repository;
	private final Clock clock;
	private volatile Map<String, Integer> cached = Map.of();
	private volatile Instant loadedAt = Instant.EPOCH;

	public ModerationSignalWeights(ModerationLearningRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	public int weightOf(String signal) {
		Instant now = clock.instant();
		if (now.isAfter(loadedAt.plusSeconds(60))) {
			cached = repository.weights();
			loadedAt = now;
		}
		return cached.getOrDefault(signal, ModerationSignalCatalog.BASE_WEIGHTS.getOrDefault(signal, 0));
	}
}
