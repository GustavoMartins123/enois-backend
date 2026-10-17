package com.socialcoisa.rede.feed.application.port.out;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.socialcoisa.rede.feed.domain.PostId;
import com.socialcoisa.rede.feed.domain.ReactionType;
import com.socialcoisa.rede.feed.domain.UserId;

public interface PostInteractionRepository {

	/**
	 * Registra a reacao do usuario no post. Distinguir "nova" de "trocada" e o que impede a contagem
	 * de subir quando a pessoa so muda o tipo da propria reacao.
	 */
	ReactionOutcome addReaction(String id, PostId postId, UserId userId, ReactionType type, Instant createdAt);

	/** Resultado de registrar uma reacao: so {@link #ADDED} e {@link #REMOVED} mexem no total. */
	enum ReactionOutcome {
		/** Nao havia reacao: o total sobe. */
		ADDED,
		/** Havia reacao de outro tipo e foi trocada: o total nao muda. */
		REPLACED,
		/** A mesma reacao foi desfeita (toggle): o total desce. */
		REMOVED
	}

	boolean removeReaction(PostId postId, UserId userId);

	boolean addSave(String id, PostId postId, UserId userId, Instant createdAt);

	boolean removeSave(PostId postId, UserId userId);

	boolean addShare(String id, PostId postId, UserId userId, Instant createdAt);

	List<String> findSavedPostIdsByUserId(UserId userId);

	Map<PostId, ReactionType> findReactionsByUserAndPostIds(UserId userId, Collection<PostId> postIds);

	List<ReactionSummary> findReactionsByPostId(PostId postId, Instant before, int limit);

	boolean addCommentReaction(String id, String commentId, UserId userId, ReactionType type, Instant createdAt);

	/** Remove a reacao do usuario no comentario (qualquer tipo). false quando nao havia. */
	boolean removeCommentReaction(String commentId, UserId userId);

	/** Reacoes do usuario para varios comentarios (id do comentario -> tipo), em uma consulta. */
	Map<String, ReactionType> findCommentReactionsByUser(UserId userId, Collection<String> commentIds);
	List<ReactionSummary> findReactionsByCommentId(String commentId);

	record ReactionSummary(String userId, ReactionType type, Instant createdAt) {
	}
}
