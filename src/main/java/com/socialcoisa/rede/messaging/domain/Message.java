package com.socialcoisa.rede.messaging.domain;

import java.time.Instant;
import java.util.List;

public class Message {

	private final String id;
	private final String conversationId;
	private final String senderId;
	private final String text;
	private final List<String> mediaIds;
	private final boolean mediaSpoiler;
	private final Instant createdAt;

	private Message(String id, String conversationId, String senderId, String text, List<String> mediaIds,
			Instant createdAt) {
		this(id, conversationId, senderId, text, mediaIds, createdAt, false);
	}

	private Message(String id, String conversationId, String senderId, String text, List<String> mediaIds,
			Instant createdAt, boolean mediaSpoiler) {
		this.id = id;
		this.conversationId = conversationId;
		this.senderId = senderId;
		this.text = text;
		this.mediaIds = mediaIds == null ? List.of() : List.copyOf(mediaIds);
		this.mediaSpoiler = mediaSpoiler;
		this.createdAt = createdAt;
	}

	public static Message create(String id, String conversationId, String senderId, String text, Instant now) {
		return create(id, conversationId, senderId, text, List.of(), now);
	}

	public static Message create(String id, String conversationId, String senderId, String text,
			List<String> mediaIds, Instant now) {
		return new Message(id, conversationId, senderId, text, mediaIds, now);
	}

	public static Message create(String id, String conversationId, String senderId, String text,
			List<String> mediaIds, boolean mediaSpoiler, Instant now) {
		return new Message(id, conversationId, senderId, text, mediaIds, now, mediaSpoiler);
	}

	public static Message restore(String id, String conversationId, String senderId, String text, Instant createdAt) {
		return restore(id, conversationId, senderId, text, List.of(), createdAt);
	}

	public static Message restore(String id, String conversationId, String senderId, String text,
			List<String> mediaIds, Instant createdAt) {
		return new Message(id, conversationId, senderId, text, mediaIds, createdAt);
	}

	public static Message restore(String id, String conversationId, String senderId, String text,
			List<String> mediaIds, boolean mediaSpoiler, Instant createdAt) {
		return new Message(id, conversationId, senderId, text, mediaIds, createdAt, mediaSpoiler);
	}

	public String id() { return id; }

	public String conversationId() { return conversationId; }

	public String senderId() { return senderId; }

	public String text() { return text; }

	/** Ate 4 midias anexadas; o texto pode vir vazio quando ha midia. */
	public List<String> mediaIds() { return mediaIds; }

	public boolean mediaSpoiler() { return mediaSpoiler; }

	public Instant createdAt() { return createdAt; }

}
