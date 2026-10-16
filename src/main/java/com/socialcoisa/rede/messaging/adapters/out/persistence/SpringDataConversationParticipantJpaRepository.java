package com.socialcoisa.rede.messaging.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataConversationParticipantJpaRepository extends JpaRepository<ConversationParticipantJpaEntity, Long> {

	@org.springframework.data.jpa.repository.Modifying
	@org.springframework.data.jpa.repository.Query("""
			UPDATE ConversationParticipantJpaEntity cp SET cp.hiddenAt = :now
			WHERE cp.userId = :userId AND cp.conversationId IN :ids""")
	int hideConversations(@org.springframework.data.repository.query.Param("userId") String userId,
			@org.springframework.data.repository.query.Param("ids") java.util.Collection<String> ids,
			@org.springframework.data.repository.query.Param("now") java.time.Instant now);

	List<ConversationParticipantJpaEntity> findByConversationId(String conversationId);

	Optional<ConversationParticipantJpaEntity> findByConversationIdAndUserId(String conversationId, String userId);

	boolean existsByConversationIdAndUserId(String conversationId, String userId);

}
