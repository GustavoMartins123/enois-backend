package com.socialcoisa.rede.games.domain;

import java.time.Instant;

/**
 * Jogo do agregador: o Énóis NÃO hospeda — o criador envia o LINK de onde o jogo está
 * publicado e, após revisão, ele roda EMBUTIDO num quadro isolado (sandbox, tela cheia).
 */
public class Game {

	private final String id;
	private final String userId;
	private final String slug;
	private final String title;
	private final String description;
	private final String cover;
	private final String url;
	private final String tags;
	private final GameStatus status;
	private final String reviewedBy;
	private final String reviewNote;
	private final Instant submittedAt;
	private final Instant reviewedAt;
	private final Instant createdAt;
	private final Instant updatedAt;
	private final GameDetails details;
	private final int plays;
	private final Instant publishedAt;
	private final Instant suspendedAt;
	/** Destaque pago (G2): o jogo sobe no catálogo enquanto o carimbo estiver no futuro. */
	private final Instant boostUntil;

	private Game(String id, String userId, String slug, String title, String description, String cover, String url,
			String tags, GameStatus status, String reviewedBy, String reviewNote, Instant submittedAt,
			Instant reviewedAt, Instant createdAt, Instant updatedAt, GameDetails details, int plays,
			Instant publishedAt, Instant suspendedAt, Instant boostUntil) {
		this.id = id;
		this.userId = userId;
		this.slug = slug;
		this.title = title;
		this.description = description;
		this.cover = cover;
		this.url = url;
		this.tags = tags;
		this.status = status;
		this.reviewedBy = reviewedBy;
		this.reviewNote = reviewNote;
		this.submittedAt = submittedAt;
		this.reviewedAt = reviewedAt;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
		this.details = details == null ? GameDetails.empty() : details;
		this.plays = plays;
		this.publishedAt = publishedAt;
		this.suspendedAt = suspendedAt;
		this.boostUntil = boostUntil;
	}

	public static Game submit(String id, String userId, String slug, String title, String description, String cover,
			String url, String tags, Instant now) {
		return submit(id, userId, slug, title, description, cover, url, tags, GameDetails.empty(), now);
	}

	/** Envio com a ficha completa (o painel manda tudo de uma vez). */
	public static Game submit(String id, String userId, String slug, String title, String description, String cover,
			String url, String tags, GameDetails details, Instant now) {
		return new Game(id, userId, slug, title, description, cover, url, tags, GameStatus.SUBMITTED, null, null,
			now, null, now, now, details, 0, null, null, null);
	}

	public static Game restore(String id, String userId, String slug, String title, String description, String cover,
			String url, String tags, GameStatus status, String reviewedBy, String reviewNote, Instant submittedAt,
			Instant reviewedAt, Instant createdAt, Instant updatedAt) {
		return restore(id, userId, slug, title, description, cover, url, tags, status, reviewedBy, reviewNote,
			submittedAt, reviewedAt, createdAt, updatedAt, GameDetails.empty(), 0, null, null, null);
	}

	public static Game restore(String id, String userId, String slug, String title, String description, String cover,
			String url, String tags, GameStatus status, String reviewedBy, String reviewNote, Instant submittedAt,
			Instant reviewedAt, Instant createdAt, Instant updatedAt, GameDetails details, int plays,
			Instant publishedAt, Instant suspendedAt, Instant boostUntil) {
		return new Game(id, userId, slug, title, description, cover, url, tags, status, reviewedBy, reviewNote,
			submittedAt, reviewedAt, createdAt, updatedAt, details, plays, publishedAt, suspendedAt, boostUntil);
	}

	public Game approve(String reviewerId, String note, Instant now) {
		return new Game(id, userId, slug, title, description, cover, url, tags, GameStatus.PUBLISHED, reviewerId,
			note, submittedAt, now, createdAt, now, details, plays, publishedAt == null ? now : publishedAt, null,
			boostUntil);
	}

	public Game reject(String reviewerId, String note, Instant now) {
		return new Game(id, userId, slug, title, description, cover, url, tags, GameStatus.REJECTED, reviewerId,
			note, submittedAt, now, createdAt, now, details, plays, publishedAt, suspendedAt, boostUntil);
	}

	public Game resubmit(Instant now) {
		return new Game(id, userId, slug, title, description, cover, url, tags, GameStatus.SUBMITTED, null, null,
			now, null, createdAt, now, details, plays, publishedAt, suspendedAt, boostUntil);
	}

	/** Edição pelo dono: a ficha e os textos mudam, o ciclo de vida e a moderação não. */
	public Game edit(String title, String description, String cover, String url, String tags, GameDetails details,
			Instant now) {
		return edit(title, description, cover, url, tags, details, null, now);
	}

	/**
	 * Edição com endereço novo: o slug pode mudar depois de criado (pedido da Mel) — quem publica
	 * escolhe o endereço na loja. Nulo mantém o atual; quem valida formato e colisão é o serviço.
	 */
	public Game edit(String title, String description, String cover, String url, String tags, GameDetails details,
			String newSlug, Instant now) {
		return new Game(id, userId, newSlug == null || newSlug.isBlank() ? slug : newSlug, title, description, cover,
			url, tags, status, reviewedBy, reviewNote, submittedAt, reviewedAt, createdAt, now,
			details == null ? this.details : details, plays, publishedAt, suspendedAt, boostUntil);
	}

	/** Moderação tira do ar (o registro, as licenças e o histórico continuam existindo). */
	public Game suspend(String reviewerId, String note, Instant now) {
		return new Game(id, userId, slug, title, description, cover, url, tags, GameStatus.SUSPENDED, reviewerId,
			note, submittedAt, now, createdAt, now, details, plays, publishedAt, now, boostUntil);
	}

	/** A moderação devolve o jogo ao ar depois de uma suspensão. */
	public Game restoreFromSuspension(String reviewerId, Instant now) {
		return new Game(id, userId, slug, title, description, cover, url, tags, GameStatus.PUBLISHED, reviewerId,
			reviewNote, submittedAt, now, createdAt, now, details, plays, publishedAt, null, boostUntil);
	}

	/** Uma sessão de jogo começou (o servidor é quem conta). */
	public Game countPlay() {
		return new Game(id, userId, slug, title, description, cover, url, tags, status, reviewedBy, reviewNote,
			submittedAt, reviewedAt, createdAt, updatedAt, details, plays + 1, publishedAt, suspendedAt, boostUntil);
	}

	/** Destaque pago (G2): o carimbo sobe para o limite — estende se já estava em destaque. */
	public Game boost(Instant until, Instant now) {
		return new Game(id, userId, slug, title, description, cover, url, tags, status, reviewedBy, reviewNote,
			submittedAt, reviewedAt, createdAt, now, details, plays, publishedAt, suspendedAt,
			until.isAfter(boostUntil == null ? Instant.EPOCH : boostUntil) ? until : boostUntil);
	}

	public Instant boostUntil() {
		return boostUntil;
	}

	public boolean boosted(Instant now) {
		return boostUntil != null && boostUntil.isAfter(now);
	}

	public boolean ownedBy(String userId) {
		return this.userId.equals(userId);
	}

	public boolean isPublished() {
		return status == GameStatus.PUBLISHED;
	}

	public String id() { return id; }
	public String userId() { return userId; }
	public String slug() { return slug; }
	public String title() { return title; }
	public String description() { return description; }
	public String cover() { return cover; }
	public String url() { return url; }
	public String tags() { return tags; }
	public GameStatus status() { return status; }
	public String reviewedBy() { return reviewedBy; }
	public String reviewNote() { return reviewNote; }
	public Instant submittedAt() { return submittedAt; }
	public Instant reviewedAt() { return reviewedAt; }
	public Instant createdAt() { return createdAt; }
	public Instant updatedAt() { return updatedAt; }
	public GameDetails details() { return details; }
	public int plays() { return plays; }
	public Instant publishedAt() { return publishedAt; }
	public Instant suspendedAt() { return suspendedAt; }

	/** Atalhos da ficha, para quem só quer um campo (view, catálogo, painel). */
	public GameStage stage() { return details.stage(); }
	public GameDistribution distribution() { return details.distribution(); }
	public int priceCoins() { return details.priceCoins(); }
	public GameAspect aspect() { return details.aspect(); }
	/** Ícone próprio do jogo (nulo = a loja cai no favicon do site de origem). */
	public String icon() { return details.icon(); }
	public String banner() { return details.banner(); }
	public String bannerHtml() { return details.bannerHtml(); }
}
