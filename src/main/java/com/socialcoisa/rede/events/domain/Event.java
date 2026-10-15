package com.socialcoisa.rede.events.domain;

import java.time.Instant;

public class Event {

	private final String id;
	private final String title;
	private final String date;
	private final String time;
	private final String format;
	private final String host;
	private final String cover;
	private final String createdBy;
	private final Instant createdAt;
	private final Instant updatedAt;
	private Instant sanitizedAt;
	private Integer finalAttendeesCount;

	private Event(String id, String title, String date, String time, String format, String host, String cover,
			String createdBy, Instant createdAt, Instant updatedAt) {
		this.id = id;
		this.title = title;
		this.date = date;
		this.time = time;
		this.format = format;
		this.host = host;
		this.cover = cover;
		this.createdBy = createdBy;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	public static Event create(String id, String title, String date, String time, String format, String host,
			String cover, String createdBy, Instant now) {
		return new Event(id, title, date, time, format, host, cover, createdBy, now, now);
	}

	public static Event restore(String id, String title, String date, String time, String format, String host,
			String cover, String createdBy, Instant createdAt, Instant updatedAt) {
		return new Event(id, title, date, time, format, host, cover, createdBy, createdAt, updatedAt);
	}

	/**
	 * Fim do evento (23:59:59 do dia informado, em UTC).
	 *
	 * A data e texto livre no schema, entao o parser aceita o formato ISO (o que o app envia) e o
	 * brasileiro dd/MM/yyyy. Quando nenhum dos dois casa, devolve vazio: sem data confiavel o evento
	 * NAO e considerado vencido — apagar dado pessoal por causa de um palpite seria pior.
	 */
	public java.util.Optional<Instant> expiresAt() {
		java.time.LocalDate parsed = parseDate(date);
		if (parsed == null) {
			return java.util.Optional.empty();
		}
		java.time.LocalTime limit = parseTime(time).orElse(java.time.LocalTime.of(23, 59, 59));
		return java.util.Optional.of(parsed.atTime(limit).toInstant(java.time.ZoneOffset.UTC));
	}

	private java.time.LocalDate parseDate(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String value = raw.trim();
		try {
			return java.time.LocalDate.parse(value);
		}
		catch (java.time.format.DateTimeParseException ignored) {
			// segue para o formato brasileiro
		}
		try {
			return java.time.LocalDate.parse(value, java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
		}
		catch (java.time.format.DateTimeParseException ignored) {
			return null;
		}
	}

	private java.util.Optional<java.time.LocalTime> parseTime(String raw) {
		if (raw == null || raw.isBlank()) {
			return java.util.Optional.empty();
		}
		String value = raw.trim();
		try {
			return java.util.Optional.of(java.time.LocalTime.parse(value.length() == 5 ? value + ":00" : value));
		}
		catch (java.time.format.DateTimeParseException ignored) {
			return java.util.Optional.empty();
		}
	}

	/** O evento ja passou (com a folga informada). Sem data confiavel, nunca vence. */
	public boolean isExpired(Instant now, java.time.Duration grace) {
		return expiresAt().map(expiry -> now.isAfter(expiry.plus(grace))).orElse(false);
	}

	public boolean isSanitized() {
		return sanitizedAt != null;
	}

	public Instant sanitizedAt() {
		return sanitizedAt;
	}

	/** Presencas confirmadas no momento da sanitizacao (o numero sobrevive sem as identidades). */
	public Integer finalAttendeesCount() {
		return finalAttendeesCount;
	}

	public void markSanitized(int attendeesCount, Instant now) {
		this.finalAttendeesCount = attendeesCount;
		this.sanitizedAt = now;
	}

	public String id() { return id; }

	public String title() { return title; }

	public String date() { return date; }

	public String time() { return time; }

	public String format() { return format; }

	public String host() { return host; }

	public String cover() { return cover; }

	public String createdBy() { return createdBy; }

	public Instant createdAt() { return createdAt; }

	public Instant updatedAt() { return updatedAt; }

}
