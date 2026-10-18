package com.socialcoisa.rede.groups.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.socialcoisa.rede.groups.domain.GroupComment;
import com.socialcoisa.rede.groups.domain.GroupPost;
import com.socialcoisa.rede.groups.domain.GroupReaction;
import com.socialcoisa.rede.groups.domain.GroupTopic;

/**
 * Tópicos e publicações do grupo — conteúdo privado, em tabelas próprias.
 *
 * Nada aqui junta com posts, tags ou mídia do feed principal: o isolamento é estrutural.
 */
public interface GroupContentRepository {

	void saveTopic(GroupTopic topic);

	Optional<GroupTopic> findTopic(String topicId);

	List<GroupTopic> listTopics(String groupId);

	boolean topicNameExists(String groupId, String name);

	/** Próxima posição livre (fim da lista), para o tópico novo nascer no lugar certo. */
	int nextTopicPosition(String groupId);

	void savePost(GroupPost post);

	Optional<GroupPost> findPost(String postId);

	/** Publicações do grupo, opcionalmente presas a um tópico, mais novas primeiro. */
	List<GroupPost> listPosts(String groupId, String topicId, Instant before, int limit);

	void saveComment(GroupComment comment);

	Optional<GroupComment> findComment(String commentId);

	List<GroupComment> listComments(String postId, int limit);

	long countComments(String postId);

	/** Uma reação por pessoa por publicação: salvar substitui a anterior. */
	void saveReaction(GroupReaction reaction);

	Optional<GroupReaction> findReaction(String postId, String userId);

	void removeReaction(String postId, String userId);

	long countReactions(String postId);
}
