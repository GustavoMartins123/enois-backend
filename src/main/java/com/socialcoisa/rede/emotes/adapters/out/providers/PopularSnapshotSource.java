package com.socialcoisa.rede.emotes.adapters.out.providers;

import java.io.InputStream;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import com.socialcoisa.rede.emotes.application.port.out.EmoteSource;
import com.socialcoisa.rede.emotes.domain.Emote;

import tools.jackson.databind.ObjectMapper;

/**
 * Retrato popular EMBARCADO no jar (`emotes/popular.json`).
 *
 * Existe para o catalogo nunca comecar vazio: o boot serve as populares na hora, sem depender de
 * um provedor que pode estar de fora (o FFZ devolve 522 com frequencia). O refresh agendado
 * substitui por uma lista mais nova quando consegue.
 */
public class PopularSnapshotSource implements EmoteSource {

	private final ObjectMapper objectMapper;
	private final Resource resource;

	public PopularSnapshotSource(ObjectMapper objectMapper) {
		this(objectMapper, new ClassPathResource("emotes/popular.json"));
	}

	PopularSnapshotSource(ObjectMapper objectMapper, Resource resource) {
		this.objectMapper = objectMapper;
		this.resource = resource;
	}

	@Override
	public String name() {
		return "POPULAR-SNAPSHOT";
	}

	@Override
	public boolean local() {
		return true;
	}

	@Override
	public List<Emote> load() throws Exception {
		try (InputStream stream = resource.getInputStream()) {
			return FfzEmoteJson.fromPopularSnapshotArray(objectMapper.readTree(stream).path("emotes"));
		}
	}
}