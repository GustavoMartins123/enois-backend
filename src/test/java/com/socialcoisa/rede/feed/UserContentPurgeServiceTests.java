package com.socialcoisa.rede.feed;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.socialcoisa.rede.feed.application.UserContentPurgeService;
import com.socialcoisa.rede.feed.application.port.in.PurgeAuthorContentUseCase;
import com.socialcoisa.rede.feed.application.port.out.CommentRepository;
import com.socialcoisa.rede.feed.application.port.out.PostRepository;
import com.socialcoisa.rede.feed.domain.Comment;
import com.socialcoisa.rede.feed.domain.Post;
import com.socialcoisa.rede.feed.domain.PostId;
import com.socialcoisa.rede.feed.domain.PostStatus;
import com.socialcoisa.rede.feed.domain.PostType;
import com.socialcoisa.rede.feed.domain.UserId;

/**
 * A purga de conteúdo (sanitização por spam): o spammer sai do banco INTEIRO — posts, os
 * comentários dele em posts alheios, os comentários nos posts dele e as respostas de terceiros
 * aos comentários apagados (viram comentário solto). Ordem importa por causa das FKs.
 */
@SpringBootTest
class UserContentPurgeServiceTests {

	@Autowired
	private PostRepository posts;

	@Autowired
	private CommentRepository comments;

	@Autowired
	private UserContentPurgeService purge;

	private static final UserId SPAMMER = new UserId("user_purge_spammer");
	private static final UserId VICTIM = new UserId("user_purge_victim");

	@Test
	void purgeRemovesPostsCommentsAndDetachesForeignReplies() {
		Instant now = Instant.now();
		posts.save(Post.publish(new PostId("post_purge_spam"), SPAMMER, PostType.INSIGHT,
			"compre seguidores", null, List.of(), List.of(), null, null, now, null));
		posts.save(Post.publish(new PostId("post_purge_victim"), VICTIM, PostType.INSIGHT,
			"post legítimo", null, List.of(), List.of(), null, null, now, null));

		// Comentário do spammer no próprio post; comentário do spammer no post da vítima;
		// e a RESPOSTA da vítima ao comentário do spammer (tem que sobreviver, sem pai).
		comments.save(Comment.create("comment_purge_own", new PostId("post_purge_spam"), SPAMMER, null,
			"comentário no próprio post", List.of(), now));
		comments.save(Comment.create("comment_purge_foreign", new PostId("post_purge_victim"), SPAMMER, null,
			"spam nos comentários alheios", List.of(), now));
		comments.save(Comment.create("comment_purge_reply", new PostId("post_purge_victim"), VICTIM,
			"comment_purge_foreign", "resposta legítima", List.of(), now));

		PurgeAuthorContentUseCase.PurgeResult result =
			purge.purgeAllByAuthor(VICTIM.value(), SPAMMER.value(), "teste de purga");

		assertThat(result.posts()).isEqualTo(1);
		assertThat(result.comments()).isEqualTo(2);
		assertThat(posts.findById(new PostId("post_purge_spam"))).isEmpty();
		assertThat(posts.findById(new PostId("post_purge_victim"))).isPresent();
		assertThat(posts.findById(new PostId("post_purge_victim")).orElseThrow().status())
			.isEqualTo(PostStatus.ACTIVE);
		assertThat(comments.findById("comment_purge_own")).isEmpty();
		assertThat(comments.findById("comment_purge_foreign")).isEmpty();
		// A resposta da vítima sobrevive, destacada (sem pai apagado).
		assertThat(comments.findById("comment_purge_reply")).isPresent();
		assertThat(comments.findById("comment_purge_reply").orElseThrow().parentCommentId()).isNull();
	}
}
