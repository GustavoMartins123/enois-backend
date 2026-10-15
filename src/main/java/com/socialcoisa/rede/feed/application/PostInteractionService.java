package com.socialcoisa.rede.feed.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

import com.socialcoisa.rede.feed.application.port.in.CommentOnPostUseCase;
import com.socialcoisa.rede.feed.application.port.in.GetCommentsUseCase;
import com.socialcoisa.rede.feed.application.port.in.ReactToPostUseCase;
import com.socialcoisa.rede.feed.application.port.in.SavePostUseCase;
import com.socialcoisa.rede.feed.application.port.in.SharePostUseCase;
import com.socialcoisa.rede.feed.application.port.out.CommentRepository;
import com.socialcoisa.rede.feed.application.port.out.OutboxEventRepository;
import com.socialcoisa.rede.feed.application.port.out.PostInteractionRepository;
import com.socialcoisa.rede.feed.application.port.out.PostRepository;
import com.socialcoisa.rede.feed.domain.Comment;
import com.socialcoisa.rede.feed.domain.CommentStatus;
import com.socialcoisa.rede.feed.domain.Post;
import com.socialcoisa.rede.feed.domain.PostId;
import com.socialcoisa.rede.notifications.application.NotificationService;
import com.socialcoisa.rede.shared.blocks.BlockedUsersReader;
import com.socialcoisa.rede.shared.web.ApiException;
import com.socialcoisa.rede.shared.web.CursorCodec;

@Service
public class PostInteractionService implements CommentOnPostUseCase, GetCommentsUseCase, ReactToPostUseCase,
		SavePostUseCase, SharePostUseCase {

	private static final Instant DEFAULT_BEFORE = Instant.parse("9999-12-31T23:59:59Z");

	private final PostRepository postRepository;
	private final CommentRepository commentRepository;
	private final PostInteractionRepository postInteractionRepository;
	private final OutboxEventRepository outboxEventRepository;
	private final CursorCodec cursorCodec;
	private final Clock clock;
	private final NotificationService notificationService;
	private final com.socialcoisa.rede.profile.application.port.out.UserProfileRepository userProfileRepository;
	private final BlockedUsersReader blockedUsersReader;

	public PostInteractionService(PostRepository postRepository, CommentRepository commentRepository,
			PostInteractionRepository postInteractionRepository, OutboxEventRepository outboxEventRepository,
			CursorCodec cursorCodec, Clock clock,
			NotificationService notificationService,
			com.socialcoisa.rede.profile.application.port.out.UserProfileRepository userProfileRepository,
			BlockedUsersReader blockedUsersReader) {
		this.postRepository = postRepository;
		this.commentRepository = commentRepository;
		this.postInteractionRepository = postInteractionRepository;
		this.outboxEventRepository = outboxEventRepository;
		this.cursorCodec = cursorCodec;
		this.clock = clock;
		this.notificationService = notificationService;
		this.userProfileRepository = userProfileRepository;
		this.blockedUsersReader = blockedUsersReader;
	}

	@Override
	@Transactional
	public CommentView comment(CommentOnPostCommand command) {
		Post post = activePost(command.postId());
		String content = normalizeContent(command.content());
		String parentCommentId = normalizeParentComment(command.parentCommentId(), post.id());
		Comment comment = Comment.create(newId("comment"), post.id(), command.authorId(), parentCommentId, content,
			clock.instant());
		Comment saved = commentRepository.save(comment);
		post.addComment();
		postRepository.save(post);
		outboxEventRepository.append("PostCommented", post.id().value(), "{\"postId\":\"" + post.id().value()
			+ "\",\"commentId\":\"" + saved.id() + "\",\"authorId\":\"" + command.authorId().value() + "\"}");
		notifyCommentReply(parentCommentId, post, command.authorId().value());
		notifyMentions(command.mentionedUserIds(), command.authorId().value(), post.id().value());
		var profile = userProfileRepository.findById(command.authorId().value()).orElse(null);
		return CommentView.from(saved,
			profile != null ? profile.name() : null,
			profile != null ? profile.username() : null,
			profile != null ? profile.avatar() : null);
	}

	/**
	 * Resposta a um comentario: quem escreveu o comentario-pai precisa ser avisado. Antes so o autor
	 * do POST era notificado (via outbox), entao quem era respondido nunca ficava sabendo. Pular o
	 * proprio autor da resposta e o autor do post (que ja recebe NEW_COMMENT) evita avisos duplicados.
	 */
	private void notifyCommentReply(String parentCommentId, Post post, String actorId) {
		if (parentCommentId == null) return;
		String parentAuthorId = commentRepository.findById(parentCommentId)
			.map(comment -> comment.authorId().value())
			.orElse(null);
		if (parentAuthorId == null) return;
		if (parentAuthorId.equals(actorId)) return;
		if (parentAuthorId.equals(post.authorId().value())) return;
		var actor = userProfileRepository.findById(actorId).orElse(null);
		String actorName = actor != null && actor.name() != null ? actor.name() : "Alguem";
		notificationService.create(parentAuthorId, "NEW_REPLY", actorId, "Nova resposta",
			actorName + " respondeu ao seu comentario.", "/api/feed/posts/" + post.id().value(), "Ver");
	}

	private void notifyMentions(List<String> mentionedUserIds, String actorId, String postId) {
		if (mentionedUserIds == null) return;
		for (String mentionedId : mentionedUserIds.stream().filter(StringUtils::hasText).map(String::trim).distinct().toList()) {
			if (mentionedId.equals(actorId)) continue;
			if (userProfileRepository.findById(mentionedId).isEmpty()) continue;
			notificationService.create(mentionedId, "MENTION", actorId,
				"Menção em comentário",
				"Você foi mencionado em um comentário.",
				"/api/feed/posts/" + postId, "Ver");
		}
	}

	@Override
	@Transactional(readOnly = true)
	public CommentsPage getComments(GetCommentsQuery query) {
		int limit = normalizeLimit(query.limit());
		Instant before = query.before().orElse(DEFAULT_BEFORE);
		activePost(query.postId());
		Set<String> hidden = query.viewerId()
			.map(viewer -> blockedUsersReader.hiddenIdsFor(viewer.value()))
			.orElse(Set.of());
		var fetched = commentRepository.findActiveByPost(query.postId(), before, limit);
		var comments = fetched.stream()
			.filter(c -> !hidden.contains(c.authorId().value()))
			.map(c -> {
				var p = userProfileRepository.findById(c.authorId().value()).orElse(null);
				return CommentView.from(c,
					p != null ? p.name() : null,
					p != null ? p.username() : null,
					p != null ? p.avatar() : null);
			})
			.toList();
		// Cursor pela pagina do banco: comentario escondido nao pode encerrar a paginacao.
		String nextCursor = fetched.size() == limit
			? cursorCodec.encode(fetched.get(fetched.size() - 1).createdAt()) : null;
		return new CommentsPage(comments, nextCursor);
	}

	@Override
	@Transactional
	public PostActionResult react(ReactToPostCommand command) {
		if (command.type() == null) {
			throw new ApiException(400, "INVALID_REACTION_TYPE", "Tipo de reacao obrigatorio.");
		}
		Post post = activePost(command.postId());
		var outcome = postInteractionRepository.addReaction(newId("reaction"), post.id(), command.userId(),
			command.type(), clock.instant());
		// Trocar o tipo da reacao (REPLACED) NAO mexe no total: a pessoa ja estava contada. Antes,
		// qualquer gravacao incrementava, e a contagem subia quando o usuario so mudava de reacao.
		switch (outcome) {
			case ADDED -> {
				post.addReaction();
				postRepository.save(post);
				outboxEventRepository.append("PostReacted", post.id().value(), "{\"postId\":\"" + post.id().value()
					+ "\",\"authorId\":\"" + command.userId().value() + "\"}");
			}
			case REMOVED -> {
				post.removeReaction();
				postRepository.save(post);
				outboxEventRepository.append("PostReactionRemoved", post.id().value(),
					"{\"postId\":\"" + post.id().value() + "\"}");
			}
			case REPLACED -> {
				// nada a fazer no contador
			}
		}
		String status = switch (outcome) {
			case ADDED -> "REACTED";
			case REPLACED -> "REACTION_CHANGED";
			case REMOVED -> "REACTION_REMOVED";
		};
		return new PostActionResult(post.id().value(), status, post.counters());
	}

	@Override
	@Transactional
	public PostActionResult removeReaction(ReactToPostCommand command) {
		Post post = activePost(command.postId());
		boolean removed = postInteractionRepository.removeReaction(post.id(), command.userId());
		if (removed) {
			post.removeReaction();
			postRepository.save(post);
			outboxEventRepository.append("PostReactionRemoved", post.id().value(), "{\"postId\":\"" + post.id().value()
				+ "\"}");
		}
		return new PostActionResult(post.id().value(), removed ? "REACTION_REMOVED" : "REACTION_NOT_FOUND",
			post.counters());
	}

	@Override
	@Transactional
	public PostActionResult save(PostActionCommand command) {
		Post post = activePost(command.postId());
		boolean added = postInteractionRepository.addSave(newId("saved_post"), post.id(), command.userId(),
			clock.instant());
		if (added) {
			post.addSave();
			postRepository.save(post);
			outboxEventRepository.append("PostSaved", post.id().value(), "{\"postId\":\"" + post.id().value() + "\"}");
		}
		return new PostActionResult(post.id().value(), added ? "SAVED" : "ALREADY_SAVED", post.counters());
	}

	@Override
	@Transactional
	public PostActionResult removeSave(PostActionCommand command) {
		Post post = activePost(command.postId());
		boolean removed = postInteractionRepository.removeSave(post.id(), command.userId());
		if (removed) {
			post.removeSave();
			postRepository.save(post);
			outboxEventRepository.append("PostSaveRemoved", post.id().value(), "{\"postId\":\"" + post.id().value()
				+ "\"}");
		}
		return new PostActionResult(post.id().value(), removed ? "SAVE_REMOVED" : "SAVE_NOT_FOUND", post.counters());
	}

	@Override
	@Transactional
	public PostActionResult share(PostActionCommand command) {
		Post post = activePost(command.postId());
		boolean added = postInteractionRepository.addShare(newId("post_share"), post.id(), command.userId(),
			clock.instant());
		if (added) {
			post.addShare();
			postRepository.save(post);
			outboxEventRepository.append("PostShared", post.id().value(), "{\"postId\":\"" + post.id().value() + "\"}");
		}
		return new PostActionResult(post.id().value(), added ? "SHARED" : "ALREADY_SHARED", post.counters());
	}

	private Post activePost(PostId postId) {
		Post post = postRepository.findById(postId)
			.orElseThrow(() -> new ApiException(404, "POST_NOT_FOUND", "Post nao encontrado."));
		if (!post.isActive()) {
			throw new ApiException(404, "POST_NOT_FOUND", "Post nao encontrado.");
		}
		return post;
	}

	private String normalizeContent(String content) {
		if (!StringUtils.hasText(content)) {
			throw new ApiException(400, "INVALID_COMMENT_CONTENT", "Conteudo obrigatorio.");
		}
		String normalized = content.trim();
		if (normalized.length() > 1024) {
			throw new ApiException(400, "COMMENT_TOO_LONG", "Comentario deve ter no maximo 1024 caracteres.");
		}
		return normalized;
	}

	private String normalizeParentComment(String parentCommentId, PostId postId) {
		if (!StringUtils.hasText(parentCommentId)) {
			return null;
		}
		Comment parent = commentRepository.findById(parentCommentId.trim())
			.orElseThrow(() -> new ApiException(404, "PARENT_COMMENT_NOT_FOUND", "Comentario pai nao encontrado."));
		if (!parent.postId().equals(postId) || parent.status() != CommentStatus.ACTIVE) {
			throw new ApiException(404, "PARENT_COMMENT_NOT_FOUND", "Comentario pai nao encontrado.");
		}
		return parent.id();
	}

	private int normalizeLimit(int limit) {
		if (limit < 1) {
			return 20;
		}
		return Math.min(limit, 50);
	}

	private String newId(String prefix) {
		return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
	}
}
