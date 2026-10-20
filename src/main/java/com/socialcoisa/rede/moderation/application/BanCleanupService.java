package com.socialcoisa.rede.moderation.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.socialcoisa.rede.feed.application.port.out.CommentRepository;
import com.socialcoisa.rede.feed.application.port.out.PostRepository;
import com.socialcoisa.rede.feed.domain.Post;
import com.socialcoisa.rede.feed.domain.Comment;
import com.socialcoisa.rede.feed.domain.PostStatus;
import com.socialcoisa.rede.feed.domain.UserId;
import com.socialcoisa.rede.feed.application.port.in.ModerationRemovePostUseCase;
import com.socialcoisa.rede.messaging.application.port.out.MessagingRepository;
import com.socialcoisa.rede.messaging.domain.Message;
import com.socialcoisa.rede.moderation.application.port.out.BanContentArchiveRepository;

/**
 * Ao banir um usuario, arquiva todo o conteudo dele (posts, comentarios, mensagens)
 * na tabela {@code ban_content_archive} e remove dos modulos de origem.
 *
 * <p>Posts e comentarios sao removidos da rede. Mensagens sao arquivadas mas
 * permanecem visiveis para o outro participante (marcadas como "usuario banido"
 * pelo frontend via sanction gate).
 */
@Service
public class BanCleanupService {

	private static final Logger LOG = LoggerFactory.getLogger(BanCleanupService.class);

	private final PostRepository postRepository;
	private final CommentRepository commentRepository;
	private final MessagingRepository messagingRepository;
	private final ModerationRemovePostUseCase removePost;
	private final BanContentArchiveRepository archive;
	private final Clock clock;

	public BanCleanupService(PostRepository postRepository, CommentRepository commentRepository,
			MessagingRepository messagingRepository, ModerationRemovePostUseCase removePost,
			BanContentArchiveRepository archive, Clock clock) {
		this.postRepository = postRepository;
		this.commentRepository = commentRepository;
		this.messagingRepository = messagingRepository;
		this.removePost = removePost;
		this.archive = archive;
		this.clock = clock;
	}

	/**
	 * Arquiva e remove todo o conteudo do usuario banido. Chamado dentro da
	 * transacao de ban (SanctionService). Falhas em itens individuais sao
	 * logadas mas nao impedem o ban.
	 */
	public void cleanup(String sanctionId, String userId) {
		Instant now = Instant.now(clock);
		int posts = archiveAndRemovePosts(sanctionId, userId, now);
		int comments = archiveAndRemoveComments(sanctionId, userId, now);
		int messages = archiveMessages(sanctionId, userId, now);
		LOG.info("Ban cleanup para usuario {}: {} posts, {} comentarios, {} mensagens arquivados",
			userId, posts, comments, messages);
	}

	private int archiveAndRemovePosts(String sanctionId, String userId, Instant now) {
		List<Post> posts = postRepository.findAllByAuthor(new UserId(userId));
		for (Post post : posts) {
			try {
				String snapshot = snapshotPost(post);
				archive.archive(sanctionId, userId, "POST", post.id().value(), snapshot, now);
				if (post.status() == PostStatus.ACTIVE) {
					removePost.removeAsModerator("ban-cleanup", post.id().value(), "BAN:" + sanctionId);
				}
			} catch (RuntimeException e) {
				LOG.warn("Falha ao arquivar post {} do usuario {}: {}", post.id().value(), userId, e.getMessage());
			}
		}
		return posts.size();
	}

	private int archiveAndRemoveComments(String sanctionId, String userId, Instant now) {
		List<Comment> comments = commentRepository.findAllByAuthor(userId);
		for (Comment comment : comments) {
			try {
				String snapshot = snapshotComment(comment);
				archive.archive(sanctionId, userId, "COMMENT", comment.id(), snapshot, now);
				commentRepository.deleteById(comment.id());
			} catch (RuntimeException e) {
				LOG.warn("Falha ao arquivar comentario {} do usuario {}: {}", comment.id(), userId, e.getMessage());
			}
		}
		return comments.size();
	}

	/** Mensagens: arquiva para registro mas NAO remove — o outro participante continua vendo. */
	private int archiveMessages(String sanctionId, String userId, Instant now) {
		List<Message> messages = messagingRepository.findAllBySender(userId);
		for (Message message : messages) {
			try {
				String snapshot = snapshotMessage(message);
				archive.archive(sanctionId, userId, "MESSAGE", message.id(), snapshot, now);
			} catch (RuntimeException e) {
				LOG.warn("Falha ao arquivar mensagem {} do usuario {}: {}", message.id(), userId, e.getMessage());
			}
		}
		return messages.size();
	}

	private String snapshotPost(Post post) {
		return "{\"id\":\"" + esc(post.id().value())
			+ "\",\"type\":\"" + post.type()
			+ "\",\"content\":\"" + esc(post.content())
			+ "\",\"status\":\"" + post.status()
			+ "\",\"createdAt\":\"" + post.createdAt()
			+ "\"}";
	}

	private String snapshotComment(Comment comment) {
		return "{\"id\":\"" + esc(comment.id())
			+ "\",\"postId\":\"" + esc(comment.postId().value())
			+ "\",\"content\":\"" + esc(comment.content())
			+ "\",\"createdAt\":\"" + comment.createdAt()
			+ "\"}";
	}

	private String snapshotMessage(Message message) {
		return "{\"id\":\"" + esc(message.id())
			+ "\",\"conversationId\":\"" + esc(message.conversationId())
			+ "\",\"text\":\"" + esc(message.text())
			+ "\",\"createdAt\":\"" + message.createdAt()
			+ "\"}";
	}

	private static String esc(String value) {
		if (value == null) return "";
		return value.replace("\\", "\\\\").replace("\"", "\\\"")
			.replace("\n", "\\n").replace("\r", "\\r");
	}
}
