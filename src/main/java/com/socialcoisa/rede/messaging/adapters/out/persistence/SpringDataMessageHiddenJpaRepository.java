package com.socialcoisa.rede.messaging.adapters.out.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataMessageHiddenJpaRepository extends JpaRepository<MessageHiddenJpaEntity, String> {

	/** Ids de mensagem que esta pessoa apagou (para a listagem filtrar). */
	@org.springframework.data.jpa.repository.Query(
		"SELECT h.messageId FROM MessageHiddenJpaEntity h WHERE h.userId = :userId AND h.messageId IN :messageIds")
	List<String> hiddenIds(@org.springframework.data.repository.query.Param("userId") String userId,
			@org.springframework.data.repository.query.Param("messageIds") Collection<String> messageIds);

	boolean existsByUserIdAndMessageId(String userId, String messageId);
}
