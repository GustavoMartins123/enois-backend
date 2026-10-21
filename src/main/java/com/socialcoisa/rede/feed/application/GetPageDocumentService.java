package com.socialcoisa.rede.feed.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.feed.application.port.out.PostRepository;
import com.socialcoisa.rede.feed.domain.Post;
import com.socialcoisa.rede.feed.domain.PostId;
import com.socialcoisa.rede.feed.domain.PostType;
import com.socialcoisa.rede.shared.web.ApiException;

@Service
public class GetPageDocumentService implements GetPageDocumentUseCase {

	private final PostRepository postRepository;

	public GetPageDocumentService(PostRepository postRepository) {
		this.postRepository = postRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public PageDocument getPageDocument(PostId postId) {
		// So pagina publicada e ativa: rascunho de outro tipo, post apagado e pagina de post comum
		// nao tem documento para servir.
		Post post = postRepository.findById(postId)
			.filter(Post::isActive)
			.filter(candidate -> candidate.type() == PostType.PAGE)
			.filter(candidate -> StringUtils.hasText(candidate.pageHtml()))
			.orElseThrow(() -> new ApiException(404, "PAGE_NOT_FOUND", "Pagina nao encontrada."));
		// Existing rows also receive the current sanitizer when served; no data rewrite required.
		String html = com.socialcoisa.rede.shared.html.SafeHtml.sanitizePageHtml(post.pageHtml());
		return new PageDocument(post.id().value(), post.authorId().value(), html == null ? "" : html);
	}
}
