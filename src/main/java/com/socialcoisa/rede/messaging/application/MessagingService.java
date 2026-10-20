package com.socialcoisa.rede.messaging.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.socialcoisa.rede.feed.domain.ReactionType;
import com.socialcoisa.rede.messaging.application.port.out.MessagingRepository;
import com.socialcoisa.rede.messaging.domain.Conversation;
import com.socialcoisa.rede.messaging.domain.ConversationParticipant;
import com.socialcoisa.rede.messaging.domain.Message;
import com.socialcoisa.rede.notifications.application.NotificationService;
import com.socialcoisa.rede.shared.web.ApiException;

@Service
public class MessagingService {

	private final MessagingRepository repository;
	private final NotificationService notificationService;
	private final Clock clock;

	public MessagingService(MessagingRepository repository, NotificationService notificationService, Clock clock) {
		this.repository = repository;
		this.notificationService = notificationService;
		this.clock = clock;
	}

	@Transactional
	public Conversation createConversation(String kind, String title, List<String> participantIds, String createdBy) {
		if (!participantIds.contains(createdBy)) {
			participantIds = new ArrayList<>(participantIds);
			participantIds.add(createdBy);
		}
		String id = "conversation_" + UUID.randomUUID().toString().replace("-", "");
		Conversation conversation = Conversation.create(id, kind, title, createdBy, clock.instant());
		repository.saveConversation(conversation);
		for (String pid : participantIds) {
			repository.saveParticipant(ConversationParticipant.create(id, pid, clock.instant()));
		}
		return conversation;
	}

	/**
	 * Conversa direta entre dois usuarios: devolve a existente ou cria (com os dois como
	 * participantes). Usada, por exemplo, para entregar uma candidatura na DM de quem publicou.
	 */
	@Transactional
	public String directConversationBetween(String userA, String userB) {
		return directConversationBetween(userA, userB, "DIRECT");
	}

	/**
	 * Conversa direta marcada com a origem. A candidatura de vaga usa kind = "JOB" para o inbox
	 * poder separar as conversas de vaga das conversas com pessoas.
	 */
	@Transactional
	public String directConversationBetween(String userA, String userB, String kind) {
		var existing = repository.findDirectBetween(userA, userB);
		if (existing.isPresent()) {
			return existing.get().id();
		}
		String id = "conversation_" + java.util.UUID.randomUUID().toString().replace("-", "");
		repository.saveConversation(Conversation.create(id, kind, null, userA, clock.instant()));
		repository.saveParticipant(ConversationParticipant.create(id, userA, clock.instant()));
		repository.saveParticipant(ConversationParticipant.create(id, userB, clock.instant()));
		return id;
	}

	@Transactional
	public Message sendMessage(String conversationId, String senderId, String text) {
		return sendMessage(conversationId, senderId, text, java.util.List.of());
	}

	@Transactional
	public Message sendMessage(String conversationId, String senderId, String text,
			java.util.List<String> mediaIds) {
		Conversation conversation = repository.findConversationById(conversationId)
			.orElseThrow(() -> new ApiException(404, "CONVERSATION_NOT_FOUND", "Conversa nao encontrada."));
		if (!repository.isParticipant(conversationId, senderId)) {
			throw new ApiException(403, "NOT_PARTICIPANT", "Voce nao participa desta conversa.");
		}
		java.util.List<String> midias = mediaIds == null ? java.util.List.of()
			: mediaIds.stream().filter(v -> v != null && !v.isBlank()).map(String::trim).distinct().toList();
		String corpo = text == null ? "" : text.trim();
		if (corpo.isEmpty() && midias.isEmpty()) {
			throw new ApiException(400, "EMPTY_MESSAGE", "Escreva uma mensagem ou anexe uma midia.");
		}
		if (midias.size() > 4) {
			throw new ApiException(400, "TOO_MANY_MEDIA", "Anexe no maximo 4 midias por mensagem.");
		}
		String id = "message_" + UUID.randomUUID().toString().replace("-", "");
		Message message = Message.create(id, conversationId, senderId, corpo, midias, clock.instant());
		repository.saveMessage(message);
		// Ao enviar, a conversa fica lida para QUEM ENVIOU (PR #15) — antes o contador continuava
		// aceso mesmo com a conversa aberta.
		repository.markRead(conversationId, senderId, clock.instant());
		// A lista de conversas ordena por updatedAt; sem este toque a conversa antiga fica no lugar
		// mesmo recebendo mensagem nova (#25 dos issues).
		repository.saveConversation(conversation.touch(clock.instant()));
		String preview = corpo.isEmpty()
			? (midias.isEmpty() ? "Nova mensagem" : "Enviou uma midia")
			: (corpo.length() > 80 ? corpo.substring(0, 77) + "..." : corpo);
		List<ConversationParticipant> participants = repository.findParticipantsByConversation(conversationId);
		for (ConversationParticipant p : participants) {
			if (p.userId().equals(senderId)) continue;
			notificationService.create(p.userId(), "NEW_MESSAGE", senderId,
				"Nova mensagem", preview,
				"/api/conversations/" + conversationId, "Responder");
		}
		return message;
	}

	@Transactional
	public void markRead(String conversationId, String userId) {
		if (!repository.isParticipant(conversationId, userId)) {
			throw new ApiException(403, "NOT_PARTICIPANT", "Voce nao participa desta conversa.");
		}
		repository.markRead(conversationId, userId, clock.instant());
	}

	public List<Conversation> listConversations(String userId, java.time.Instant before, int limit) {
		return repository.findConversationsByUserId(userId, before, limit);
	}

	public int hideConversations(String userId, java.util.List<String> ids) {
		return repository.hideConversations(userId, ids, clock.instant());
	}

	/** As conversas arquivadas (apagadas so para quem chama). */
	public List<Conversation> listArchivedConversations(String userId, java.time.Instant before, int limit) {
		return repository.findArchivedConversationsByUserId(userId, before, limit);
	}

	/** Restaurar: a conversa volta para a caixa de entrada de quem chamou. */
	public int unhideConversations(String userId, java.util.List<String> ids) {
		return repository.unhideConversations(userId, ids);
	}

	public boolean isParticipant(String conversationId, String userId) {
		return repository.isParticipant(conversationId, userId);
	}

	/**
	 * A mensagem existe e e mesmo desta conversa.
	 *
	 * O caminho da reacao recebe o id da conversa e o id da mensagem separados: sem esta conferencia,
	 * o id da conversa era decorativo e dava para reagir (e ler reacoes) em mensagem de conversa alheia.
	 */
	public boolean messageBelongsTo(String messageId, String conversationId) {
		return repository.findMessageById(messageId)
			.map(message -> message.conversationId().equals(conversationId))
			.orElse(false);
	}

	public Conversation getConversation(String id) {
		return repository.findConversationById(id)
			.orElseThrow(() -> new ApiException(404, "CONVERSATION_NOT_FOUND", "Conversa nao encontrada."));
	}

	public List<ConversationParticipant> getParticipants(String conversationId) {
		return repository.findParticipantsByConversation(conversationId);
	}

	public int countUnread(String conversationId, String userId) {
		return repository.countUnread(conversationId, userId);
	}

	public List<Message> getMessages(String conversationId, String viewerId, java.time.Instant before, int limit) {
		if (!repository.findConversationById(conversationId).isPresent()) {
			throw new ApiException(404, "CONVERSATION_NOT_FOUND", "Conversa nao encontrada.");
		}
		// O leitor entra na consulta: a mensagem que ELE apagou nao volta (para o outro continua).
		return repository.findMessagesByConversation(conversationId, viewerId, before, limit);
	}

	/** "Apagar para mim" por mensagem: some so para quem apagou. */
	public int hideMessages(String userId, java.util.List<String> messageIds) {
		return repository.hideMessages(userId, messageIds, clock.instant());
	}

	public String getLastMessageText(String conversationId) {
		return repository.findLastMessage(conversationId)
			.map(Message::text)
			.orElse(null);
	}

	public java.time.Instant getLastMessageTime(String conversationId) {
		return repository.findLastMessage(conversationId)
			.map(Message::createdAt)
			.orElse(null);
	}

	@Transactional
	public boolean addReaction(String messageId, String userId, ReactionType type) {
		String id = "mreac_" + UUID.randomUUID().toString().replace("-", "");
		return repository.addMessageReaction(id, messageId, userId, type, clock.instant());
	}

	public List<MessagingRepository.ReactionSummary> getReactions(String messageId) {
		return repository.findReactionsByMessageId(messageId);
	}

}
