package com.socialcoisa.rede.feed.adapters.out.moderation;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.socialcoisa.rede.feed.application.port.out.CommentRepository;
import com.socialcoisa.rede.feed.application.port.out.PostRepository;
import com.socialcoisa.rede.feed.domain.PostId;
import com.socialcoisa.rede.moderation.application.port.out.ReportedContentAuthorReader;

/** Autor do conteudo denunciado, para a sancao saber em quem recai. */
@Component
class ReportedContentAuthorAdapter implements ReportedContentAuthorReader {

	private final PostRepository posts;
	private final CommentRepository comments;

	ReportedContentAuthorAdapter(PostRepository posts, CommentRepository comments) {
		this.posts = posts;
		this.comments = comments;
	}

	@Override
	public Optional<String> authorOf(String targetType, String targetId) {
		return switch (targetType) {
			case "POST" -> posts.findById(new PostId(targetId)).map(p -> p.authorId().value());
			case "COMMENT" -> comments.findById(targetId).map(c -> c.authorId().value());
			default -> Optional.empty();
		};
	}
}
