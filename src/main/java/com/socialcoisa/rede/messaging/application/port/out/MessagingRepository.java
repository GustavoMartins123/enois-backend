package com.socialcoisa.rede.messaging.application.port.out;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.socialcoisa.rede.messaging.domain.Conversation;
import com.socialcoisa.rede.messaging.domain.ConversationParticipant;
import com.socialcoisa.rede.messaging.domain.Message;

public interface MessagingRepository {

	void saveConversation(Conversation conversation);

	void saveParticipant(ConversationParticipant participant);

	Optional<Conversation> findConversationById(String id);

	List<Conversation> findConversationsByUserId(String userId, java.time.Instant before, int limit);

	/** As conversas que a pessoa arquivou (apagadas so para ela). */
	List<Conversation> findArchivedConversationsByUserId(String userId, java.time.Instant before, int limit);

	List<ConversationParticipant> findParticipantsByConversation(String conversationId);

	/** Conversa DIRETA (1:1) entre dois usuarios, se existir. */
	Optional<Conversation> findDirectBetween(String userA, String userB);

	Optional<ConversationParticipant> findParticipant(String conversationId, String userId);

	boolean isParticipant(String conversationId, String userId);

	/** Esconde conversas para um usuario ("apagar para mim"); nao afeta os outros participantes. */
	int hideConversations(String userId, Collection<String> conversationIds, java.time.Instant now);

	/** Desfaz o "apagar para mim" (a conversa volta para a caixa de entrada). */
	int unhideConversations(String userId, Collection<String> conversationIds);

	void markRead(String conversationId, String userId, java.time.Instant now);

	int countUnread(String conversationId, String userId);

	void saveMessage(Message message);

	List<Message> findMessagesByConversation(String conversationId, java.time.Instant before, int limit);

	Optional<Message> findLastMessage(String conversationId);

	/** A mensagem por id (para conferir de qual conversa ela e antes de mexer nela). */
	Optional<Message> findMessageById(String messageId);

	boolean addMessageReaction(String id, String messageId, String userId, com.socialcoisa.rede.feed.domain.ReactionType type, java.time.Instant createdAt);
	boolean removeMessageReaction(String messageId, String userId);
	List<ReactionSummary> findReactionsByMessageId(String messageId);

	record ReactionSummary(String userId, com.socialcoisa.rede.feed.domain.ReactionType type, java.time.Instant createdAt) {}
}
