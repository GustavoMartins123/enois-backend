package com.socialcoisa.rede.preferences.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.socialcoisa.rede.preferences.application.port.out.UserPreferenceRepository;
import com.socialcoisa.rede.preferences.domain.ContentTaxonomy;
import com.socialcoisa.rede.preferences.domain.UserPreference;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Os três controles de personalização — com efeitos DISTINTOS (diretriz da dona):
 *
 * 1. likedTopics ("O que quero ver"): AUMENTA A PRIORIDADE nas recomendações. Nunca elimina
 *    o resto do feed — uma configuração vazia é o comportamento padrão.
 * 2. blockedTopics ("Assuntos que não gosto"): FILTRA posts classificados com o assunto nas
 *    superfícies definidas. Não é censura: a publicação continua no ar para as outras pessoas
 *    e acessível por link direto. Post SEM tópico nunca é bloqueado.
 * 3. disabledModules ("O que não quero ver"): desativa módulos na experiência pessoal.
 *    Nunca apaga dados; EVENTS desativado desativa também o "Acontecendo agora".
 *
 * Fonte única de verdade: esta tabela no backend, aplicada consistentemente em todas as
 * superfícies (menu, notificações, cards, recomendações).
 */
@Service
public class PersonalizationService {

	private final UserPreferenceRepository repository;
	private final Clock clock;

	public PersonalizationService(UserPreferenceRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	public record Personalization(List<String> likedTopics, List<String> blockedTopics,
			List<String> disabledModules) {
	}

	public Personalization of(String userId) {
		return repository.find(userId)
			.map(p -> new Personalization(parse(p.likedTopics()), parse(p.blockedTopics()),
				parse(p.disabledModules())))
			.orElseGet(() -> new Personalization(List.of(), List.of(), List.of()));
	}

	/**
	 * Atualização parcial: cada lista é validada item a item contra a taxonomia/módulos reais
	 * (valor desconhecido é recusa, não silêncio), deduplicada e gravada. Listas nulas ou
	 * vazias = volta ao padrão. O tema da conta é preservado.
	 */
	public Personalization update(String userId, List<String> likedTopics, List<String> blockedTopics,
			List<String> disabledModules) {
		UserPreference existing = repository.find(userId).orElse(null);
		String liked = validateAndJoin(likedTopics, ContentTaxonomy.TOPIC_SET, "assunto");
		String blocked = validateAndJoin(blockedTopics, ContentTaxonomy.TOPIC_SET, "assunto");
		String modules = validateAndJoin(disabledModules, ContentTaxonomy.MODULE_SET, "módulo");
		// Um assunto não pode estar em "quero ver" e "não gosto" ao mesmo tempo: a dupla
		// configuração conflitante só criaria comportamento indefinido nos feeds.
		Set<String> likedSet = Set.copyOf(parse(liked));
		for (String topic : parse(blocked)) {
			if (likedSet.contains(topic)) {
				throw new ApiException(409, "CONFLICTING_PREFERENCE",
					"\"" + topic + "\" não pode estar em \"quero ver\" e em \"não gosto\" ao mesmo tempo.");
			}
		}
		repository.save(new UserPreference(userId,
			existing == null ? null : existing.themeId(), liked, blocked, modules, clock.instant()));
		return of(userId);
	}

	private String validateAndJoin(List<String> values, Set<String> allowed, String kind) {
		if (values == null || values.isEmpty()) {
			return null;
		}
		Set<String> deduped = new LinkedHashSet<>();
		for (String value : values) {
			String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
			if (normalized.isEmpty()) {
				continue;
			}
			if (!allowed.contains(normalized)) {
				throw new ApiException(400, "INVALID_" + kind.toUpperCase(Locale.ROOT),
					"Valor desconhecido para " + kind + ": " + normalized);
			}
			deduped.add(normalized);
		}
		return deduped.isEmpty() ? null : String.join(",", deduped);
	}

	private List<String> parse(String csv) {
		if (csv == null || csv.isBlank()) {
			return List.of();
		}
		List<String> items = new ArrayList<>();
		for (String item : csv.split(",")) {
			if (!item.isBlank()) {
				items.add(item.trim());
			}
		}
		return List.copyOf(items);
	}
}
