package com.socialcoisa.rede.feed.application;

import java.time.Clock;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.feed.application.port.in.EditPostUseCase;
import com.socialcoisa.rede.feed.application.port.out.AuditLogRepository;
import com.socialcoisa.rede.feed.application.port.out.FeedCacheRepository;
import com.socialcoisa.rede.feed.application.port.out.PostRepository;
import com.socialcoisa.rede.feed.domain.Post;
import com.socialcoisa.rede.feed.domain.PostType;
import com.socialcoisa.rede.profile.application.port.out.UserProfileRepository;
import com.socialcoisa.rede.shared.html.SafeHtml;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Edicao de publicacao propria. So o autor edita (validado no backend, nao no cliente) e o conteudo
 * segue as mesmas regras do post novo. Tipo, midias e comunidade ficam como estao; a pagina (posts
 * do tipo PAGE) e re-sanitizada a cada edicao, porque o HTML vem de fora de novo.
 */
@Service
public class EditPostService implements EditPostUseCase {

	private static final int MAX_CONTENT = 1200;
	private static final int MAX_TAGS = 5;

	private final PostRepository postRepository;
	private final FeedCacheRepository feedCacheRepository;
	private final AuditLogRepository auditLogRepository;
	private final UserProfileRepository userProfileRepository;
	private final Clock clock;

	public EditPostService(PostRepository postRepository, FeedCacheRepository feedCacheRepository,
			AuditLogRepository auditLogRepository, UserProfileRepository userProfileRepository, Clock clock) {
		this.postRepository = postRepository;
		this.feedCacheRepository = feedCacheRepository;
		this.auditLogRepository = auditLogRepository;
		this.userProfileRepository = userProfileRepository;
		this.clock = clock;
	}

	@Override
	@Transactional
	public PostView edit(EditPostCommand command) {
		Post post = postRepository.findById(command.postId())
			.orElseThrow(() -> new ApiException(404, "POST_NOT_FOUND", "Post nao encontrado."));
		if (!post.belongsTo(command.userId())) {
			throw new ApiException(403, "POST_OWNER_MISMATCH", "Post nao pertence ao usuario autenticado.");
		}
		if (!post.isActive()) {
			throw new ApiException(404, "POST_NOT_FOUND", "Post nao encontrado.");
		}
		String content = validateContent(command.content());
		List<String> tags = normalizeTags(command.tags());

		Post edited = post.edited(content, pageHtml(post, command), tags, clock.instant());
		Post saved = postRepository.save(edited);
		feedCacheRepository.removeEverywhere(saved.id());
		auditLogRepository.record(command.userId(), "POST_EDITED", saved.id().value(), null);

		var author = userProfileRepository.findById(saved.authorId().value()).orElse(null);
		return author != null
			? PostView.from(saved, author.username(), author.name(), author.avatar())
			: PostView.from(saved);
	}

	/**
	 * A pagina acompanha o tipo do post (nao o que o cliente manda): num PAGE ela e obrigatoria,
	 * nos outros o HTML recebido e descartado.
	 */
	private String pageHtml(Post post, EditPostCommand command) {
		if (post.type() != PostType.PAGE) {
			return null;
		}
		return SafeHtml.requirePageHtml(command.pageHtml());
	}

	private String validateContent(String content) {
		if (!StringUtils.hasText(content)) {
			throw new ApiException(400, "INVALID_CONTENT", "Conteudo obrigatorio.");
		}
		String normalized = content.trim();
		if (normalized.length() > MAX_CONTENT) {
			throw new ApiException(400, "CONTENT_TOO_LONG", "Conteudo deve ter no maximo 1200 caracteres.");
		}
		return normalized;
	}

	private List<String> normalizeTags(List<String> tags) {
		List<String> normalized = tags == null ? List.of() : tags.stream()
			.filter(StringUtils::hasText)
			.map(String::trim)
			.distinct()
			.toList();
		if (normalized.size() > MAX_TAGS) {
			throw new ApiException(400, "TOO_MANY_TAGS", "Post deve ter no maximo 5 tags.");
		}
		return normalized;
	}
}
