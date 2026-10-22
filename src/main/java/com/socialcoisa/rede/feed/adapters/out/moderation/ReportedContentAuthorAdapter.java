package com.socialcoisa.rede.feed.adapters.out.moderation;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.socialcoisa.rede.feed.application.port.out.CommentRepository;
import com.socialcoisa.rede.feed.application.port.out.PostRepository;
import com.socialcoisa.rede.feed.domain.PostId;
import com.socialcoisa.rede.groups.application.port.out.GroupContentRepository;
import com.socialcoisa.rede.groups.domain.GroupComment;
import com.socialcoisa.rede.groups.domain.GroupPost;
import com.socialcoisa.rede.moderation.application.port.out.ReportedContentAuthorReader;

/**
 * Autor do conteudo denunciado, para a sancao saber em quem recai.
 *
 * Conteudo de grupo tambem resolve aqui: a publicacao e o comentario do feed privado tem autor
 * inequivoco, como os do feed principal.
 */
@Component
class ReportedContentAuthorAdapter implements ReportedContentAuthorReader {

	private final PostRepository posts;
	private final CommentRepository comments;
	private final GroupContentRepository groupContent;

	ReportedContentAuthorAdapter(PostRepository posts, CommentRepository comments,
			GroupContentRepository groupContent) {
		this.posts = posts;
		this.comments = comments;
		this.groupContent = groupContent;
	}

	@Override
	public Optional<String> authorOf(String targetType, String targetId) {
		return switch (targetType) {
			case "POST" -> posts.findById(new PostId(targetId)).map(p -> p.authorId().value());
			case "COMMENT" -> comments.findById(targetId).map(c -> c.authorId().value());
			// Denuncia de perfil: o alvo E a pessoa. Sem isto, penalizar/banir um perfil denunciado
			// (spam, por exemplo) batia em "alvo sem autor" e nada acontecia.
			case "USER" -> Optional.of(targetId);
			case "GROUP_POST" -> groupContent.findPost(targetId).map(GroupPost::authorId);
			case "GROUP_COMMENT" -> groupContent.findComment(targetId).map(GroupComment::authorId);
			default -> Optional.empty();
		};
	}
}
