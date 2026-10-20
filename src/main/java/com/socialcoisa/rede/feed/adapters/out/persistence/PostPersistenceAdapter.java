package com.socialcoisa.rede.feed.adapters.out.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.feed.application.port.out.PostRepository;
import com.socialcoisa.rede.feed.domain.Post;
import com.socialcoisa.rede.feed.domain.PostId;
import com.socialcoisa.rede.feed.domain.PostStatus;
import com.socialcoisa.rede.feed.domain.PostType;
import com.socialcoisa.rede.feed.domain.UserId;

@Repository
class PostPersistenceAdapter implements PostRepository {

	private final SpringDataPostJpaRepository repository;

	PostPersistenceAdapter(SpringDataPostJpaRepository repository) {
		this.repository = repository;
	}

	@Override
	public Post save(Post post) {
		return repository.save(PostJpaEntity.fromDomain(post)).toDomain();
	}

	@Override
	public List<Post> findExpiredMoments(Instant now, int limit) {
		return repository.findByTypeAndStatusAndExpiresAtBefore(PostType.MOMENT, PostStatus.ACTIVE, now).stream()
			.limit(Math.max(1, limit))
			.map(PostJpaEntity::toDomain).toList();
	}

	@Override
	public Optional<Post> findById(PostId id) {
		return repository.findById(id.value()).map(PostJpaEntity::toDomain);
	}

	@Override
	public List<Post> findByIds(Collection<PostId> ids) {
		if (ids == null || ids.isEmpty()) {
			return List.of();
		}
		return repository.findByIdIn(ids.stream().map(PostId::value).toList()).stream()
			.map(PostJpaEntity::toDomain)
			.toList();
	}

	@Override
	public List<Post> findActiveByAuthors(Set<UserId> authorIds, Instant before, int limit) {
		if (authorIds == null || authorIds.isEmpty()) {
			return List.of();
		}
		return repository.findByAuthorIdInAndStatusAndCreatedAtBeforeOrderByCreatedAtDesc(
			authorIds.stream().map(UserId::value).toList(), PostStatus.ACTIVE, before, PageRequest.of(0, limit)).stream()
			.map(PostJpaEntity::toDomain)
			.toList();
	}

	@Override
	public List<Post> findActiveByAuthor(UserId authorId, Instant before, int limit) {
		// Ponteiro de blog fica fora do perfil: o conteudo dele mora no blog, nao na lista de posts.
		return repository.findByAuthorIdAndStatusAndTypeNotAndCreatedAtBeforeOrderByCreatedAtDesc(authorId.value(),
			PostStatus.ACTIVE, PostType.BLOG, before, PageRequest.of(0, limit)).stream()
			.map(PostJpaEntity::toDomain)
			.toList();
	}

	@Override
	public List<Post> findActiveAll(Instant before, int limit) {
		// "Tudo" nunca mostra post de blog: quem quer acompanhar se inscreve no blog, e a novidade
		// aparece no "Para voce" de quem segue.
		return repository.findByStatusAndTypeNotAndCreatedAtBeforeOrderByCreatedAtDesc(PostStatus.ACTIVE,
			PostType.BLOG, before, PageRequest.of(0, limit)).stream()
			.map(PostJpaEntity::toDomain)
			.toList();
	}

	@Override
	public List<Post> findActiveByType(PostType type, Instant before, int limit) {
		return repository.findByTypeAndStatusAndCreatedAtBeforeOrderByCreatedAtDesc(type, PostStatus.ACTIVE, before,
			PageRequest.of(0, limit)).stream()
			.map(PostJpaEntity::toDomain)
			.toList();
	}

	@Override
	public List<Post> findAllByAuthor(UserId authorId) {
		return repository.findByAuthorId(authorId.value()).stream()
			.map(PostJpaEntity::toDomain)
			.toList();
	}

	@Override
	public List<Post> findActiveByCommunity(String communityId, Instant before, int limit) {
		if (communityId == null || communityId.isBlank()) {
			return List.of();
		}
		return repository.findByCommunityIdAndStatusAndCreatedAtBeforeOrderByCreatedAtDesc(communityId.trim(),
			PostStatus.ACTIVE, before, PageRequest.of(0, limit)).stream()
			.map(PostJpaEntity::toDomain)
			.toList();
	}
}
