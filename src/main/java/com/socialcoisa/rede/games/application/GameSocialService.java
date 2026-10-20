package com.socialcoisa.rede.games.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.games.application.port.out.GameCommentRepository;
import com.socialcoisa.rede.games.application.port.out.GameRepository;
import com.socialcoisa.rede.games.application.port.out.GameReviewRepository;
import com.socialcoisa.rede.games.domain.Game;
import com.socialcoisa.rede.games.domain.GameComment;
import com.socialcoisa.rede.games.domain.GameReview;
import com.socialcoisa.rede.profile.application.port.out.UserProfileRepository;
import com.socialcoisa.rede.profile.domain.UserProfile;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Avaliações (nota 1-5 + texto) e comentários dos jogos.
 *
 * Regras: o DONO não avalia o próprio jogo (evita inflar a nota); cada pessoa tem UMA
 * avaliação por jogo (reenviar atualiza); comentários são livres, mas o dono do jogo e o
 * autor podem apagar.
 */
@Service
public class GameSocialService {

	private final GameReviewRepository reviews;
	private final GameCommentRepository comments;
	private final GameRepository games;
	private final UserProfileRepository profiles;
	private final Clock clock;

	public GameSocialService(GameReviewRepository reviews, GameCommentRepository comments, GameRepository games,
			UserProfileRepository profiles, Clock clock) {
		this.reviews = reviews;
		this.comments = comments;
		this.games = games;
		this.profiles = profiles;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public ReviewsView reviews(String gameId, int limit) {
		Game game = game(gameId);
		List<ReviewView> items = reviews.findByGame(game.id(), limit).stream().map(this::reviewView).toList();
		return new ReviewsView(reviews.averageByGame(game.id()), reviews.countByGame(game.id()), items);
	}

	@Transactional
	public ReviewView review(String gameId, String userId, int rating, String body) {
		Game game = game(gameId);
		if (game.userId().equals(userId)) {
			throw new ApiException(403, "OWNER_CANNOT_REVIEW", "Você não pode avaliar o próprio jogo.");
		}
		if (rating < 1 || rating > 5) {
			throw new ApiException(400, "INVALID_RATING", "A nota vai de 1 a 5 estrelas.");
		}
		String cleanBody = trimOrNull(body, 2000, "O texto da avaliação é longo demais.");
		Instant now = clock.instant();
		GameReview existing = reviews.findByGameAndUser(gameId, userId).orElse(null);
		GameReview saved = existing == null
			? reviews.save(GameReview.create("grev_" + randomId(), gameId, userId, rating, cleanBody, now))
			: reviews.save(existing.update(rating, cleanBody, now));
		return reviewView(saved);
	}

	@Transactional
	public void deleteReview(String gameId, String userId) {
		game(gameId);
		GameReview existing = reviews.findByGameAndUser(gameId, userId)
			.orElseThrow(() -> new ApiException(404, "REVIEW_NOT_FOUND", "Você ainda não avaliou este jogo."));
		reviews.delete(existing.id());
	}

	@Transactional(readOnly = true)
	public CommentsView comments(String gameId, int limit) {
		Game game = game(gameId);
		return new CommentsView(comments.countByGame(game.id()),
			comments.findByGame(game.id(), limit).stream().map(this::commentView).toList());
	}

	@Transactional
	public CommentView comment(String gameId, String userId, String body) {
		game(gameId);
		String clean = trimOrNull(body, 2000, "O comentário é longo demais.");
		if (clean == null || clean.length() < 2) {
			throw new ApiException(400, "COMMENT_REQUIRED", "Escreva um comentário.");
		}
		GameComment saved = comments.save(GameComment.create("gcm_" + randomId(), gameId, userId, clean,
			clock.instant()));
		return commentView(saved);
	}

	@Transactional
	public void deleteComment(String gameId, String commentId, String userId) {
		Game game = game(gameId);
		GameComment comment = comments.findById(commentId)
			.orElseThrow(() -> new ApiException(404, "COMMENT_NOT_FOUND", "Comentário não encontrado."));
		if (!comment.gameId().equals(gameId)) {
			throw new ApiException(404, "COMMENT_NOT_FOUND", "Comentário não encontrado.");
		}
		if (!comment.userId().equals(userId) && !game.userId().equals(userId)) {
			throw new ApiException(403, "FORBIDDEN", "Só o autor ou o dono do jogo pode apagar.");
		}
		comments.delete(commentId);
	}

	private Game game(String gameId) {
		return games.findById(gameId)
			.orElseThrow(() -> new ApiException(404, "GAME_NOT_FOUND", "Jogo não encontrado."));
	}

	private ReviewView reviewView(GameReview review) {
		UserProfile profile = profiles.findById(review.userId()).orElse(null);
		return new ReviewView(review.id(), review.userId(),
			profile == null ? null : profile.username(), profile == null ? null : profile.displayName(),
			profile == null ? null : profile.avatar(), review.rating(), review.body(),
			review.createdAt().toString(), review.updatedAt().toString());
	}

	private CommentView commentView(GameComment comment) {
		UserProfile profile = profiles.findById(comment.userId()).orElse(null);
		return new CommentView(comment.id(), comment.userId(),
			profile == null ? null : profile.username(), profile == null ? null : profile.displayName(),
			profile == null ? null : profile.avatar(), comment.body(), comment.createdAt().toString());
	}

	private static String trimOrNull(String value, int max, String tooLong) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		String clean = value.trim();
		if (clean.length() > max) {
			throw new ApiException(400, "TEXT_TOO_LONG", tooLong);
		}
		return clean;
	}

	private static String randomId() {
		return UUID.randomUUID().toString().replace("-", "");
	}

	public record ReviewsView(double average, long count, List<ReviewView> items) {
	}

	public record ReviewView(String id, String userId, String username, String name, String avatar, int rating,
			String body, String createdAt, String updatedAt) {
	}

	public record CommentsView(long count, List<CommentView> items) {
	}

	public record CommentView(String id, String userId, String username, String name, String avatar, String body,
			String createdAt) {
	}
}
