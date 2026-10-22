package com.socialcoisa.rede.coin.domain;

import java.time.Instant;

/** Contestação de transferência (golpe): OPEN → (UPHELD = reversão | REJECTED = falsa). */
public record CoinDispute(String id, String entryRef, String senderId, String receiverId, long amount,
		String reason, String description, State state, String reportId, String reviewNote, String reviewedBy,
		Instant reviewedAt, Instant createdAt) {

	public enum State {
		OPEN, UPHELD, REJECTED
	}

	public static CoinDispute open(String id, String entryRef, String senderId, String receiverId, long amount,
			String reason, String description, String reportId, Instant now) {
		return new CoinDispute(id, entryRef, senderId, receiverId, amount, reason, description, State.OPEN, reportId,
			null, null, null, now);
	}

	public boolean pending() {
		return state == State.OPEN;
	}

	public CoinDispute uphold(String reviewerId, String note, Instant now) {
		return new CoinDispute(id, entryRef, senderId, receiverId, amount, reason, description, State.UPHELD,
			reportId, note, reviewerId, now, createdAt);
	}

	public static CoinDispute restore(String id, String entryRef, String senderId, String receiverId, long amount,
			String reason, String description, State state, String reportId, String reviewNote, String reviewedBy,
			Instant reviewedAt, Instant createdAt) {
		return new CoinDispute(id, entryRef, senderId, receiverId, amount, reason, description, state, reportId,
			reviewNote, reviewedBy, reviewedAt, createdAt);
	}

	public CoinDispute reject(String reviewerId, String note, Instant now) {
		return new CoinDispute(id, entryRef, senderId, receiverId, amount, reason, description, State.REJECTED,
			reportId, note, reviewerId, now, createdAt);
	}
}
