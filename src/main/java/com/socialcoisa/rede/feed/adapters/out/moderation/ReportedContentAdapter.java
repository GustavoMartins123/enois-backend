package com.socialcoisa.rede.feed.adapters.out.moderation;

import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.feed.application.port.out.CommentRepository;
import com.socialcoisa.rede.feed.application.port.out.PostRepository;
import com.socialcoisa.rede.feed.domain.PostId;
import com.socialcoisa.rede.groups.application.port.out.GroupContentRepository;
import com.socialcoisa.rede.groups.domain.GroupComment;
import com.socialcoisa.rede.groups.domain.GroupPost;
import com.socialcoisa.rede.moderation.application.port.out.ReportedContentReader;
import com.socialcoisa.rede.profile.application.port.out.UserProfileRepository;
import com.socialcoisa.rede.profile.domain.UserProfile;

/**
 * Conteudo do alvo denunciado para a triagem por IA.
 *
 * Post traz o texto e, quando for pagina, o HTML (e ali que o conteudo mora); comentario traz o
 * texto; perfil traz o que a pessoa escreveu sobre si — o que da para julgar num perfil denunciado
 * como spam. Alvo sem texto (vaga, ou ja apagado) devolve vazio, e a triagem assume que nao tem
 * conteudo em vez de julgar pelo id.
 */
@Component
class ReportedContentAdapter implements ReportedContentReader {

	/** Teto do que vai ao modelo: o suficiente para julgar, sem estourar o prompt. */
	private static final int MAX_CHARS = 4_000;

	private final PostRepository posts;
	private final CommentRepository comments;
	private final UserProfileRepository profiles;
	private final GroupContentRepository groupContent;

	ReportedContentAdapter(PostRepository posts, CommentRepository comments, UserProfileRepository profiles,
			GroupContentRepository groupContent) {
		this.posts = posts;
		this.comments = comments;
		this.profiles = profiles;
		this.groupContent = groupContent;
	}

	@Override
	public Optional<String> contentOf(String targetType, String targetId) {
		Optional<String> content = switch (targetType == null ? "" : targetType) {
			case "POST" -> posts.findById(new PostId(targetId)).map(this::postText);
			case "COMMENT" -> comments.findById(targetId).map(comment -> comment.content());
			case "USER" -> profiles.findById(targetId).map(this::profileText);
			case "GROUP_POST" -> groupContent.findPost(targetId).map(GroupPost::content);
			case "GROUP_COMMENT" -> groupContent.findComment(targetId).map(GroupComment::content);
			default -> Optional.empty();
		};
		return content.map(this::shorten).filter(StringUtils::hasText);
	}

	private String postText(com.socialcoisa.rede.feed.domain.Post post) {
		// Pagina e uma coisa OU outra: quando o HTML existe, e ele o conteudo (o texto de cima e
		// so o titulo) — entao os dois entram, com o HTML primeiro.
		return Stream.of(post.pageHtml(), post.content())
			.filter(StringUtils::hasText)
			.reduce((first, second) -> first + "\n\n" + second)
			.orElse("");
	}

	private String profileText(UserProfile profile) {
		return Stream.of(profile.name(), profile.headline(), profile.company(), profile.location(), profile.bio())
			.filter(StringUtils::hasText)
			.reduce((first, second) -> first + "\n" + second)
			.orElse("");
	}

	private String shorten(String content) {
		String trimmed = content.trim();
		return trimmed.length() <= MAX_CHARS ? trimmed : trimmed.substring(0, MAX_CHARS);
	}
}
