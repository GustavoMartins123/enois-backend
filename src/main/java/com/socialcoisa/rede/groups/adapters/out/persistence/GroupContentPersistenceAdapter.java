package com.socialcoisa.rede.groups.adapters.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.groups.application.port.out.GroupContentRepository;
import com.socialcoisa.rede.groups.domain.GroupPost;
import com.socialcoisa.rede.groups.domain.GroupPostStatus;
import com.socialcoisa.rede.groups.domain.GroupTopic;

@Repository
class GroupContentPersistenceAdapter implements GroupContentRepository {

	private final SpringDataGroupTopicJpaRepository topics;
	private final SpringDataGroupPostJpaRepository posts;

	GroupContentPersistenceAdapter(SpringDataGroupTopicJpaRepository topics,
			SpringDataGroupPostJpaRepository posts) {
		this.topics = topics;
		this.posts = posts;
	}

	@Override
	public void saveTopic(GroupTopic topic) {
		topics.save(GroupTopicJpaEntity.fromDomain(topic));
	}

	@Override
	public Optional<GroupTopic> findTopic(String topicId) {
		return topics.findById(topicId).map(GroupTopicJpaEntity::toDomain);
	}

	@Override
	public List<GroupTopic> listTopics(String groupId) {
		return topics.findByGroupIdOrderByPositionAsc(groupId).stream().map(GroupTopicJpaEntity::toDomain).toList();
	}

	@Override
	public boolean topicNameExists(String groupId, String name) {
		return topics.existsByGroupIdAndNameIgnoreCase(groupId, name);
	}

	@Override
	public int nextTopicPosition(String groupId) {
		return topics.maxPosition(groupId) + 1;
	}

	@Override
	public void savePost(GroupPost post) {
		posts.save(GroupPostJpaEntity.fromDomain(post));
	}

	@Override
	public List<GroupPost> listPosts(String groupId, String topicId, Instant before, int limit) {
		return posts.list(groupId, GroupPostStatus.PUBLISHED, topicId, before, PageRequest.of(0, limit))
			.stream()
			.map(GroupPostJpaEntity::toDomain)
			.toList();
	}
}
