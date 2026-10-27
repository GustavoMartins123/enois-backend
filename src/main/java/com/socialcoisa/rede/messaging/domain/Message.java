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
	private final boolean e2e;
	private final String iv;
	private final Instant createdAt;

	private Message(String id, String conversationId, String senderId, String text, List<String> mediaIds,
			Instant createdAt) {
		this(id, conversationId, senderId, text, mediaIds, createdAt, false, false, null);
	}

	private Message(String id, String conversationId, String senderId, String text, List<String> mediaIds,
			Instant createdAt, boolean mediaSpoiler) {
		this(id, conversationId, senderId, text, mediaIds, createdAt, mediaSpoiler, false, null);
	}

	private Message(String id, String conversationId, String senderId, String text, List<String> mediaIds,
			Instant createdAt, boolean mediaSpoiler, boolean e2e, String iv) {
		this.id = id;
		this.conversationId = conversationId;
		this.senderId = senderId;
		this.text = text;
		this.mediaIds = mediaIds == null ? List.of() : List.copyOf(mediaIds);
		this.mediaSpoiler = mediaSpoiler;
		this.e2e = e2e;
		this.iv = iv;
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

	public static Message createE2e(String id, String conversationId, String senderId, String ciphertext,
			String iv, List<String> mediaIds, boolean mediaSpoiler, Instant now) {
		return new Message(id, conversationId, senderId, ciphertext, mediaIds, now, mediaSpoiler, true, iv);
	}

	public static Message restoreE2e(String id, String conversationId, String senderId, String ciphertext,
			String iv, List<String> mediaIds, boolean mediaSpoiler, Instant createdAt) {
		return new Message(id, conversationId, senderId, ciphertext, mediaIds, createdAt, mediaSpoiler, true, iv);
	}

	public String id() { return id; }

	public String conversationId() { return conversationId; }

	public String senderId() { return senderId; }

	public String text() { return text; }

	/** Ate 4 midias anexadas; o texto pode vir vazio quando ha midia. */
	public List<String> mediaIds() { return mediaIds; }

	public boolean mediaSpoiler() { return mediaSpoiler; }

	public boolean e2e() { return e2e; }

	public String iv() { return iv; }

	public Instant createdAt() { return createdAt; }

}
