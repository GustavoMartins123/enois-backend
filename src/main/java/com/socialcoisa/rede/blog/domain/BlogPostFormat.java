package com.socialcoisa.rede.blog.domain;

/**
 * Como o autor escreveu o corpo do post do blog.
 *
 * HTML: ja sanitizado no backend (mesmo quadro do resto do app). MARKDOWN: markdown simples
 * (titulos, negrito, listas, citacao, link, imagem, codigo), que o front renderiza como texto
 * formatado — o caminho de quem sabe usar o Word ou o Obsidian e nao quer escrever HTML.
 */
public enum BlogPostFormat {
	HTML,
	MARKDOWN
}
