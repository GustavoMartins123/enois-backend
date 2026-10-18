package com.socialcoisa.rede.groups.application;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.groups.application.port.out.GroupContentRepository;
import com.socialcoisa.rede.groups.application.port.out.GroupRepository;
import com.socialcoisa.rede.groups.domain.Group;
import com.socialcoisa.rede.groups.domain.GroupCapability;
import com.socialcoisa.rede.groups.domain.GroupMember;
import com.socialcoisa.rede.groups.domain.GroupPermissions;
import com.socialcoisa.rede.groups.domain.GroupPost;
import com.socialcoisa.rede.groups.domain.GroupRole;
import com.socialcoisa.rede.groups.domain.GroupTopic;
import com.socialcoisa.rede.shared.text.PlainText;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Tópicos e feed privado do grupo.
 *
 * A autorização é a mesma tabela de capacidades do resto do módulo: ler exige ser membro
 * (`VIEW_PRIVATE_CONTENT`), administrar tópicos exige `MANAGE_TOPICS`, publicar exige `PUBLISH`.
 *
 * A publicação SEMPRE confere que o tópico é daquele grupo — o id existir não basta. A V81 ficou
 * sem chave estrangeira composta (a collation de produção quebrava o boot), então a checagem mora
 * aqui, e é testada.
 */
@Service
public class GroupFeedService {

	private static final int TOPIC_NAME_MAX = 80;
	private static final int TOPIC_DESCRIPTION_MAX = 300;
	private static final int MAX_PAGE = 50;

	private final GroupRepository groups;
	private final GroupContentRepository content;
	private final Clock clock;

	public GroupFeedService(GroupRepository groups, GroupContentRepository content, Clock clock) {
		this.groups = groups;
		this.content = content;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<GroupTopic> listTopics(String groupId, String actorId) {
		requireMember(groupId, actorId);
		return content.listTopics(groupId);
	}

	@Transactional
	public GroupTopic createTopic(String groupId, String actorId, String name, String description) {
		require(roleOf(groupId, actorId), GroupCapability.MANAGE_TOPICS);
		if (!StringUtils.hasText(name)) {
			throw new ApiException(400, "INVALID_TOPIC_NAME", "Nome do topico e obrigatorio.");
		}
		String finalName = name.trim();
		if (finalName.length() > TOPIC_NAME_MAX) {
			throw new ApiException(400, "INVALID_TOPIC_NAME", "Nome do topico deve ter ate 80 caracteres.");
		}
		if (StringUtils.hasText(description) && description.trim().length() > TOPIC_DESCRIPTION_MAX) {
			throw new ApiException(400, "INVALID_TOPIC_DESCRIPTION",
				"Descricao do topico deve ter ate 300 caracteres.");
		}
		if (content.topicNameExists(groupId, finalName)) {
			throw new ApiException(409, "TOPIC_EXISTS", "Ja existe um topico com esse nome.");
		}
		GroupTopic topic = GroupTopic.create(newId("group_topic"), groupId, finalName, trimToNull(description),
			content.nextTopicPosition(groupId), clock.instant());
		content.saveTopic(topic);
		return topic;
	}

	@Transactional
	public GroupTopic updateTopic(String groupId, String topicId, String name, String description, String actorId) {
		require(roleOf(groupId, actorId), GroupCapability.MANAGE_TOPICS);
		GroupTopic topic = requireTopic(groupId, topicId);
		String finalName = name == null ? null : name.trim();
		if (finalName != null) {
			if (finalName.isEmpty()) {
				throw new ApiException(400, "INVALID_TOPIC_NAME", "Nome do topico e obrigatorio.");
			}
			if (finalName.length() > TOPIC_NAME_MAX) {
				throw new ApiException(400, "INVALID_TOPIC_NAME", "Nome do topico deve ter ate 80 caracteres.");
			}
			if (!finalName.equalsIgnoreCase(topic.name()) && content.topicNameExists(groupId, finalName)) {
				throw new ApiException(409, "TOPIC_EXISTS", "Ja existe um topico com esse nome.");
			}
		}
		String finalDescription = description == null ? null : description.trim();
		if (finalDescription != null && finalDescription.length() > TOPIC_DESCRIPTION_MAX) {
			throw new ApiException(400, "INVALID_TOPIC_DESCRIPTION",
				"Descricao do topico deve ter ate 300 caracteres.");
		}
		GroupTopic updated = topic.update(finalName, finalDescription, clock.instant());
		content.saveTopic(updated);
		return updated;
	}

	@Transactional
	public GroupTopic archiveTopic(String groupId, String topicId, String actorId) {
		require(roleOf(groupId, actorId), GroupCapability.MANAGE_TOPICS);
		GroupTopic archived = requireTopic(groupId, topicId).archive(clock.instant());
		content.saveTopic(archived);
		return archived;
	}

	/** Reordena a lista inteira: a ordem enviada precisa conter TODOS os tópicos do grupo, uma vez. */
	@Transactional
	public List<GroupTopic> reorderTopics(String groupId, List<String> orderedTopicIds, String actorId) {
		require(roleOf(groupId, actorId), GroupCapability.MANAGE_TOPICS);
		List<GroupTopic> atuais = content.listTopics(groupId);
		Set<String> enviados = orderedTopicIds == null ? Set.of() : new HashSet<>(orderedTopicIds);
		Set<String> existentes = new HashSet<>(atuais.stream().map(GroupTopic::id).toList());
		if (orderedTopicIds == null || enviados.size() != orderedTopicIds.size()
				|| !enviados.equals(existentes)) {
			throw new ApiException(400, "INVALID_ORDER", "A ordem precisa listar todos os topicos do grupo.");
		}
		Instant now = clock.instant();
		for (int index = 0; index < orderedTopicIds.size(); index++) {
			String topicId = orderedTopicIds.get(index);
			GroupTopic topic = atuais.stream().filter(item -> item.id().equals(topicId)).findFirst()
				.orElseThrow(() -> new ApiException(400, "INVALID_ORDER", "Topico desconhecido na ordem."));
			content.saveTopic(topic.withPosition(index, now));
		}
		return content.listTopics(groupId);
	}

	@Transactional
	public GroupPost publish(String groupId, String topicId, String actorId, String text, List<String> mediaIds) {
		Group group = requireGroup(groupId);
		require(roleOf(groupId, actorId), GroupCapability.PUBLISH);
		if (!group.status().acceptsNewContent()) {
			throw new ApiException(409, "GROUP_NOT_ACCEPTING_CONTENT", "Este grupo nao esta aceitando publicacoes.");
		}
		GroupTopic topic = requireTopic(groupId, topicId);
		if (!topic.acceptsPosts()) {
			throw new ApiException(409, "TOPIC_ARCHIVED", "Este topico esta arquivado.");
		}
		if (!StringUtils.hasText(text)) {
			throw new ApiException(400, "EMPTY_CONTENT", "Escreva alguma coisa antes de publicar.");
		}
		String clean = PlainText.defuse(text.trim());
		if (clean.length() > GroupPost.MAX_CONTENT) {
			throw new ApiException(400, "CONTENT_TOO_LONG",
				"Publicacao de grupo deve ter ate 1.200 caracteres.");
		}
		GroupPost post = GroupPost.publish(newId("group_post"), groupId, topicId, actorId, clean, mediaIds,
			clock.instant());
		content.savePost(post);
		return post;
	}

	@Transactional(readOnly = true)
	public List<GroupPost> listPosts(String groupId, String topicId, String actorId, Instant before, int limit) {
		requireMember(groupId, actorId);
		return content.listPosts(groupId, StringUtils.hasText(topicId) ? topicId : null, before,
			Math.min(Math.max(limit, 1), MAX_PAGE));
	}

	private Group requireGroup(String groupId) {
		return groups.findById(groupId)
			.orElseThrow(() -> new ApiException(404, "GROUP_NOT_FOUND", "Grupo nao encontrado."));
	}

	private GroupTopic requireTopic(String groupId, String topicId) {
		return content.findTopic(topicId)
			.filter(topic -> topic.groupId().equals(groupId))
			.orElseThrow(() -> new ApiException(404, "TOPIC_NOT_FOUND", "Topico nao encontrado neste grupo."));
	}

	private GroupRole roleOf(String groupId, String actorId) {
		return groups.findMember(groupId, actorId).map(GroupMember::role).orElse(null);
	}

	private void requireMember(String groupId, String actorId) {
		require(roleOf(groupId, actorId), GroupCapability.VIEW_PRIVATE_CONTENT);
	}

	private void require(GroupRole role, GroupCapability capability) {
		if (!GroupPermissions.can(role, capability)) {
			throw new ApiException(403, "FORBIDDEN", "Voce nao tem permissao para isso neste grupo.");
		}
	}

	private static String newId(String prefix) {
		return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
