package com.socialcoisa.rede.gifs.application;

/**
 * Um GIF pronto para o seletor: `url` e a midia direta (.gif) que o comentario aceita — a mesma que
 * o reconhecimento de link do front ja renderiza — e `previewUrl` e a versao leve da grade.
 */
public record GifItem(String id, String url, String previewUrl, int width, int height, String description,
		String provider) {
}
