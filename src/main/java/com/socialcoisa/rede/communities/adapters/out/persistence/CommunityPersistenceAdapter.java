package com.socialcoisa.rede.communities.adapters.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.communities.application.port.out.CommunityRepository;
import com.socialcoisa.rede.communities.domain.Community;
import com.socialcoisa.rede.communities.domain.CommunityMember;
import com.socialcoisa.rede.communities.domain.MemberRole;

@Repository
class CommunityPersistenceAdapter implements CommunityRepository {

	private final SpringDataCommunityJpaRepository springData;
	private final SpringDataCommunityMemberJpaRepository memberRepo;
	private final SpringDataFavoriteCommunityJpaRepository favoriteRepo;

	CommunityPersistenceAdapter(SpringDataCommunityJpaRepository springData,
			SpringDataCommunityMemberJpaRepository memberRepo,
			SpringDataFavoriteCommunityJpaRepository favoriteRepo) {
		this.springData = springData;
		this.memberRepo = memberRepo;
		this.favoriteRepo = favoriteRepo;
	}

	@Override
	public void save(Community community) {
		springData.save(CommunityJpaEntity.fromDomain(community));
	}

	@Override
	public Optional<Community> findById(String id) {
		return springData.findById(id).map(CommunityJpaEntity::toDomain);
	}

	@Override
	public List<Community> findAll(String topic, Instant before, int limit) {
		PageRequest page = PageRequest.of(0, limit);
		List<CommunityJpaEntity> entities;
		if (StringUtils.hasText(topic)) {
			entities = springData.findByTopic(topic, before, page);
		} else {
			entities = springData.findAllBefore(before, page);
		}
		return entities.stream().map(CommunityJpaEntity::toDomain).toList();
	}

	@Override
	public List<Community> findByMember(String userId, int limit) {
		List<String> communityIds = memberRepo.findByUserId(userId).stream()
			.map(CommunityMemberJpaEntity::communityId)
			.limit(limit)
			.toList();
		if (communityIds.isEmpty()) {
			return List.of();
		}
		return springData.findAllById(communityIds).stream().map(CommunityJpaEntity::toDomain).toList();
	}

	@Override
	public void saveMember(CommunityMember member) {
		memberRepo.save(CommunityMemberJpaEntity.fromDomain(member));
	}

	@Override
	public void updateMemberRole(String communityId, String userId, MemberRole role) {
		memberRepo.findByCommunityIdAndUserId(communityId, userId).ifPresent(entity -> {
			entity.changeRole(role);
			memberRepo.save(entity);
		});
	}

	@Override
	public void removeAllMembers(String communityId) {
		memberRepo.deleteByCommunityId(communityId);
	}

	@Override
	public void addFavorite(String communityId, String userId, Instant createdAt) {
		if (favoriteRepo.existsByUserIdAndCommunityId(userId, communityId)) {
			return;
		}
		String id = "fav_" + java.util.UUID.randomUUID().toString().replace("-", "");
		favoriteRepo.save(new FavoriteCommunityJpaEntity(id, communityId, userId, createdAt));
	}

	@Override
	public boolean removeFavorite(String communityId, String userId) {
		if (!favoriteRepo.existsByUserIdAndCommunityId(userId, communityId)) {
			return false;
		}
		favoriteRepo.deleteByUserIdAndCommunityId(userId, communityId);
		return true;
	}

	@Override
	public List<Community> findFavorites(String userId, int limit) {
		List<String> ids = favoriteRepo.findByUserIdOrderByCreatedAtDesc(userId).stream()
			.map(FavoriteCommunityJpaEntity::getCommunityId)
			.limit(limit)
			.toList();
		if (ids.isEmpty()) {
			return List.of();
		}
		// findById em lote preservando a ordem de favoritagem.
		var byId = springData.findAllById(ids).stream()
			.collect(java.util.stream.Collectors.toMap(entity -> entity.toDomain().id(), entity -> entity));
		return ids.stream().map(byId::get).filter(java.util.Objects::nonNull)
			.map(CommunityJpaEntity::toDomain).toList();
	}

	@Override
	public void removeMember(String communityId, String userId) {
		memberRepo.deleteByCommunityIdAndUserId(communityId, userId);
	}

	@Override
	public boolean isMember(String communityId, String userId) {
		return memberRepo.existsByCommunityIdAndUserId(communityId, userId);
	}

	@Override
	public int countMembers(String communityId) {
		return memberRepo.countByCommunityId(communityId);
	}

	@Override
	public List<CommunityMember> findMembersByCommunity(String communityId) {
		return memberRepo.findByCommunityId(communityId).stream()
			.map(CommunityMemberJpaEntity::toDomain)
			.toList();
	}

	@Override
	public void delete(String communityId) {
		springData.deleteById(communityId);
	}
}
