package com.socialcoisa.rede.feed.adapters.out.persistence;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.socialcoisa.rede.feed.domain.CommentStatus;

interface SpringDataCommentJpaRepository extends JpaRepository<CommentJpaEntity, String> {

	List<CommentJpaEntity> findByPostIdAndStatusAndCreatedAtBeforeOrderByCreatedAtDesc(String postId,
			CommentStatus status, Instant before, Pageable pageable);

	List<CommentJpaEntity> findByAuthorId(String authorId);

	/**
	 * Comentários que citam o termo E trazem URL de GIF — a busca de GIF da comunidade
	 * (sem provedor externo, usa o que a rede mesma já compartilhou).
	 */
	@org.springframework.data.jpa.repository.Query("""
		select c.content from CommentJpaEntity c
		where lower(c.content) like lower(concat('%', :term, '%'))
		  and (lower(c.content) like '%.gif%'
		       or lower(c.content) like '%media.giphy.com%'
		       or lower(c.content) like '%c.tenor.com%'
		       or lower(c.content) like '%media.tenor.com%')
		order by c.createdAt desc
		""")
	List<String> searchGifContents(@org.springframework.data.repository.query.Param("term") String term,
			Pageable pageable);
}
