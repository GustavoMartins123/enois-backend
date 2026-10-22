package com.socialcoisa.rede.gifs.application;

import java.util.List;

/**
 * Resultado da busca do seletor. `available=false` quando NENHUM provedor tem chave configurada: o
 * front usa isso para explicar a indisponibilidade em vez de mostrar "nenhum resultado" — que seria
 * mentira (a busca nem chegou a acontecer).
 */
public record GifSearchResult(boolean available, String provider, List<GifItem> items) {

	public static GifSearchResult unavailable() {
		return new GifSearchResult(false, null, List.of());
	}
}
