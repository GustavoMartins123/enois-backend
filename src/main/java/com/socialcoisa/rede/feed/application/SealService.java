package com.socialcoisa.rede.feed.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.socialcoisa.rede.feed.application.port.out.PostRepository;
import com.socialcoisa.rede.feed.application.port.out.PostSealRepository;
import com.socialcoisa.rede.feed.domain.PostSeal;
import com.socialcoisa.rede.feed.domain.PostSealType;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Selos de post: a marcacao da comunidade no topo do post.
 *
 * Cada selo e independente — a pessoa pode marcar o mesmo post com varios (IMPORTANTE e DICA), e o
 * clique alterna SO aquele selo: clicar de novo no mesmo tira, clicar em outro acrescenta. O que nao
 * pode repetir e o par (pessoa, selo) no mesmo post, e quem garante isso e o indice unico da tabela.
 *
 * A leitura vem em lote (`sealsFor`) para a pagina do feed nao fazer uma consulta por post; a lista
 * de quem marcou (`people`) e uma consulta por post, como a de quem reagiu.
 */
@Service
public class SealService {

	private final PostSealRepository seals;
	private final PostRepository posts;

	public SealService(PostSealRepository seals, PostRepository posts) {
		this.seals = seals;
		this.posts = posts;
	}

	/** O post precisa existir: sem isto qualquer id inventado vira linha de selo no banco. */
	private void requirePost(String postId) {
		if (!org.springframework.util.StringUtils.hasText(postId)) {
			throw new ApiException(404, "POST_NOT_FOUND", "Post nao encontrado.");
		}
		posts.findById(new com.socialcoisa.rede.feed.domain.PostId(postId))
			.orElseThrow(() -> new ApiException(404, "POST_NOT_FOUND", "Post nao encontrado."));
	}

	/** Alterna UM selo do usuario no post e devolve o estado atual (contagens + os meus). */
	@Transactional
	public SealsView toggle(String userId, String postId, PostSealType seal) {
		if (seal == null) {
			throw new ApiException(400, "INVALID_SEAL", "Selo invalido.");
		}
		requirePost(postId);
		if (seals.findByPostAndUserAndSeal(postId, userId, seal).isPresent()) {
			seals.delete(postId, userId, seal);
		}
		else {
			seals.save(PostSeal.create("seal_" + java.util.UUID.randomUUID().toString().replace("-", ""), postId,
				userId, seal, java.time.Instant.now()));
		}
		return sealsFor(List.of(postId), userId).get(postId);
	}

	/** Tira TODOS os selos do usuario no post e devolve o estado atual. */
	@Transactional
	public SealsView remove(String userId, String postId) {
		requirePost(postId);
		seals.deleteAll(postId, userId);
		return sealsFor(List.of(postId), userId).get(postId);
	}

	/** Selos de uma lista de posts (uma consulta) + os selos do leitor em cada um. */
	@Transactional(readOnly = true)
	public Map<String, SealsView> sealsFor(List<String> postIds, String viewerId) {
		Map<String, SealsView> result = new HashMap<>();
		if (postIds == null || postIds.isEmpty()) {
			return result;
		}
		List<String> ids = postIds.stream().distinct().toList();
		Map<String, List<PostSeal>> byPost = new HashMap<>();
		for (PostSeal seal : seals.findByPostIds(ids)) {
			byPost.computeIfAbsent(seal.postId(), key -> new ArrayList<>()).add(seal);
		}
		for (String postId : ids) {
			List<PostSeal> list = byPost.getOrDefault(postId, List.of());
			List<PostSealType> mine = list.stream()
				.filter(seal -> viewerId != null && seal.userId().equals(viewerId))
				.map(PostSeal::seal)
				.toList();
			result.put(postId, new SealsView(counts(list), mine));
		}
		return result;
	}

	/**
	 * Quem marcou o post, com o selo de cada um — e as contagens no topo.
	 *
	 * `hidden` e quem nao deve aparecer (bloqueio nos dois sentidos, como na lista de quem reagiu): a
	 * contagem tambem sai da lista filtrada, senao o numero no topo nao bate com as pessoas listadas.
	 */
	@Transactional(readOnly = true)
	public SealPeopleView people(String postId, Set<String> hidden) {
		List<PostSeal> all = seals.findByPostId(postId);
		List<PostSeal> visible = all.stream()
			.filter(seal -> hidden == null || !hidden.contains(seal.userId()))
			.toList();
		List<SealPeopleView.PersonSeal> people = visible.stream()
			.map(seal -> new SealPeopleView.PersonSeal(seal.userId(), seal.seal(), seal.createdAt()))
			.toList();
		return new SealPeopleView(postId, counts(visible), people);
	}

	/** Contagem por selo, do maior para o menor (empate pelo nome, para a ordem nao dancar). */
	private List<SealsView.SealCount> counts(List<PostSeal> list) {
		Map<PostSealType, Integer> counts = new LinkedHashMap<>();
		for (PostSeal seal : list) {
			counts.merge(seal.seal(), 1, Integer::sum);
		}
		return counts.entrySet().stream()
			.map(entry -> new SealsView.SealCount(entry.getKey(), entry.getValue()))
			.sorted(Comparator.comparingInt(SealsView.SealCount::count).reversed()
				.thenComparing(count -> count.seal().name()))
			.toList();
	}
}
