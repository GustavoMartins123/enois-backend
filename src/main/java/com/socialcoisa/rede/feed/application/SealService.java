package com.socialcoisa.rede.feed.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.socialcoisa.rede.feed.application.port.out.PostRepository;
import com.socialcoisa.rede.feed.application.port.out.PostSealRepository;
import com.socialcoisa.rede.feed.domain.PostSeal;
import com.socialcoisa.rede.feed.domain.PostSealType;
import com.socialcoisa.rede.feed.application.SealsView.SealCount;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Selos de post: a marcacao da comunidade no topo do post.
 *
 * Regra igual a da reacao: UM selo por pessoa por post. Clicar no mesmo selo remove; clicar em outro
 * troca. A leitura vem em lote (`sealsFor`) para a pagina do feed nao fazer uma consulta por post.
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

	/** Alterna o selo do usuario no post e devolve o estado atual (contagens + o meu). */
	@Transactional
	public SealsView toggle(String userId, String postId, PostSealType seal) {
		if (seal == null) {
			throw new ApiException(400, "INVALID_SEAL", "Selo invalido.");
		}
		requirePost(postId);
		PostSeal existing = seals.findByPostAndUser(postId, userId).orElse(null);
		if (existing != null && existing.seal() == seal) {
			seals.delete(postId, userId);
		}
		else {
			if (existing != null) {
				seals.delete(postId, userId);
			}
			seals.save(PostSeal.create("seal_" + java.util.UUID.randomUUID().toString().replace("-", ""), postId,
				userId, seal, java.time.Instant.now()));
		}
		return sealsFor(List.of(postId), userId).get(postId);
	}

	/** Tira o selo do usuario no post (nao importa qual era) e devolve o estado atual. */
	@Transactional
	public SealsView remove(String userId, String postId) {
		requirePost(postId);
		seals.delete(postId, userId);
		return sealsFor(List.of(postId), userId).get(postId);
	}

	/** Selos de uma lista de posts (uma consulta) + o selo do leitor em cada um. */
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
			Map<PostSealType, Integer> counts = new HashMap<>();
			PostSealType mine = null;
			for (PostSeal seal : list) {
				counts.merge(seal.seal(), 1, Integer::sum);
				if (viewerId != null && seal.userId().equals(viewerId)) {
					mine = seal.seal();
				}
			}
			List<SealsView.SealCount> ordered = counts.entrySet().stream()
				.map(entry -> new SealsView.SealCount(entry.getKey(), entry.getValue()))
				.sorted(Comparator.comparingInt(SealsView.SealCount::count).reversed()
					.thenComparing(count -> count.seal().name()))
				.toList();
			result.put(postId, new SealsView(ordered, mine));
		}
		return result;
	}
}
