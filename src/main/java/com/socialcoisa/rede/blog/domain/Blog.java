package com.socialcoisa.rede.blog.domain;

import java.time.Instant;

/**
 * O blog de uma pessoa. Um por usuario (`user_id` e unico) e com slug proprio — hoje o slug e o
 * username de quem criou, porque o endereco pedido e `enois.ia.br/blog/{username}`.
 *
 * `themeHtml` e a liberdade de customizacao do cantinho: HTML/CSS sanitizado que vira a cara da
 * pagina do blog (portfolio, diario, o que a pessoa quiser). Nulo = cabecalho padrao.
 */
public final class Blog {

	private final String id;
	private final String userId;
	private String slug;
	private String title;
	private String description;
	private String themeHtml;
	private final Instant createdAt;
	private Instant updatedAt;

	private Blog(String id, String userId, String slug, String title, String description, String themeHtml,
			Instant createdAt, Instant updatedAt) {
		this.id = id;
		this.userId = userId;
		this.slug = slug;
		this.title = title;
		this.description = description;
		this.themeHtml = themeHtml;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	public static Blog create(String id, String userId, String slug, String title, String description, Instant now) {
		return new Blog(id, userId, slug, title, description, null, now, now);
	}

	public static Blog restore(String id, String userId, String slug, String title, String description, String themeHtml,
			Instant createdAt, Instant updatedAt) {
		return new Blog(id, userId, slug, title, description, themeHtml, createdAt, updatedAt);
	}

	/** Edicao do cantinho: nulo mantem o que ja estava (mesma regra dos outros campos de perfil). */
	public void update(String title, String description, String themeHtml, Instant now) {
		this.title = keep(title, this.title);
		this.description = keep(description, this.description);
		if (themeHtml != null) {
			this.themeHtml = themeHtml;
		}
		this.updatedAt = now;
	}

	private static String keep(String incoming, String current) {
		return incoming == null ? current : incoming;
	}

	public String id() { return id; }

	public String userId() { return userId; }

	public String slug() { return slug; }

	public String title() { return title; }

	public String description() { return description; }

	public String themeHtml() { return themeHtml; }

	public Instant createdAt() { return createdAt; }

	public Instant updatedAt() { return updatedAt; }
}
