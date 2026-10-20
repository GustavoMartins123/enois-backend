package com.socialcoisa.rede.feed.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.socialcoisa.rede.feed.domain.Comment;
import com.socialcoisa.rede.feed.domain.PostId;

public interface CommentRepository {

	Comment save(Comment comment);

	Optional<Comment> findById(String id);

	List<Comment> findActiveByPost(PostId postId, Instant before, int limit);

	void deleteById(String id);

	List<String> findPostIdsByAuthor(String authorId);

	/** TODOS os comentários do autor, em qualquer status — purga por spam e ban cleanup. */
	List<Comment> findAllByAuthor(String authorId);

	/** Todos os comentários (qualquer status) dos posts dados — uso da purga. */
	List<Comment> findAllByPostIds(java.util.Collection<String> postIds);
}
