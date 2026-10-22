package com.socialcoisa.rede.gifs.adapters.out.providers;

import java.util.List;

import com.socialcoisa.rede.gifs.application.GifItem;
import com.socialcoisa.rede.gifs.application.port.out.CommunityGifReader;
import com.socialcoisa.rede.gifs.application.port.out.GifProvider;

/**
 * Provedor SEMPRE disponível: busca nos GIFs que a própria rede já compartilhou
 * (comentários com URL de GIF citando o termo). É o que mantém o seletor
 * funcionando sem chave de provedor externo — a rede alimenta a rede.
 */
public class CommunityGifProvider implements GifProvider {

	private final CommunityGifReader reader;

	public CommunityGifProvider(CommunityGifReader reader) {
		this.reader = reader;
	}

	@Override
	public String name() {
		return "Comunidade";
	}

	@Override
	public boolean available() {
		return true;
	}

	@Override
	public List<GifItem> search(String query, int limit) {
		return reader.search(query == null ? "" : query.toLowerCase(), limit).stream()
			.map(url -> new GifItem(url, url, url, 0, 0, null, name()))
			.toList();
	}
}
