package com.socialcoisa.rede.messaging.adapters.out.persistence;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataConversationJpaRepository extends JpaRepository<ConversationJpaEntity, String> {

	/** Conversas do usuario, ignorando as que ele escondeu ("apagar para mim"). (ver findByParticipantUserId) */

	/**
	 * Conversa DIRETA entre exatamente dois usuarios. Considera DIRECT e JOB (candidatura) para
	 * reaproveitar a mesma thread em vez de duplicar; filtra pelos dois participantes e pela
	 * contagem, para nao casar com um grupo que tenha as mesmas duas pessoas.
	 */
	@Query("""
			SELECT c FROM ConversationJpaEntity c
			WHERE c.kind IN ('DIRECT', 'JOB')
			AND c.id IN (SELECT cp.conversationId FROM ConversationParticipantJpaEntity cp WHERE cp.userId = :userA)
			AND c.id IN (SELECT cp.conversationId FROM ConversationParticipantJpaEntity cp WHERE cp.userId = :userB)
			AND (SELECT COUNT(all_) FROM ConversationParticipantJpaEntity all_ WHERE all_.conversationId = c.id) = 2
			ORDER BY c.updatedAt DESC""")
	List<ConversationJpaEntity> findDirectBetween(@Param("userA") String userA, @Param("userB") String userB,
			Pageable pageable);

	/**
	 * A conversa de SERVICO entre as duas pessoas. Ela e SEPARADA da conversa direta: e o que
	 * da a aba "Servicos" na caixa de entrada e o que guarda o assunto do pedido.
	 */
	@Query("""
			SELECT c FROM ConversationJpaEntity c
			WHERE c.kind = 'SERVICE'
			AND c.id IN (SELECT cp.conversationId FROM ConversationParticipantJpaEntity cp WHERE cp.userId = :userA)
			AND c.id IN (SELECT cp.conversationId FROM ConversationParticipantJpaEntity cp WHERE cp.userId = :userB)
			AND (SELECT COUNT(all_) FROM ConversationParticipantJpaEntity all_ WHERE all_.conversationId = c.id) = 2
			ORDER BY c.updatedAt DESC""")
	List<ConversationJpaEntity> findServiceBetween(@Param("userA") String userA, @Param("userB") String userB,
			Pageable pageable);

	@Query("""
			SELECT c FROM ConversationJpaEntity c
			WHERE c.id IN (SELECT cp.conversationId FROM ConversationParticipantJpaEntity cp
				WHERE cp.userId = :userId AND cp.hiddenAt IS NULL)
			AND (:before IS NULL OR c.updatedAt < :before)
			ORDER BY c.updatedAt DESC""")
	List<ConversationJpaEntity> findByParticipantUserId(@Param("userId") String userId, @Param("before") Instant before,
			Pageable pageable);

	/** As que a pessoa apagou para si ("arquivadas"): a caixa de entrada esconde, esta lista mostra. */
	@Query("""
			SELECT c FROM ConversationJpaEntity c
			WHERE c.id IN (SELECT cp.conversationId FROM ConversationParticipantJpaEntity cp
				WHERE cp.userId = :userId AND cp.hiddenAt IS NOT NULL)
			AND (:before IS NULL OR c.updatedAt < :before)
			ORDER BY c.updatedAt DESC""")
	List<ConversationJpaEntity> findArchivedByParticipantUserId(@Param("userId") String userId,
			@Param("before") Instant before, Pageable pageable);

}
