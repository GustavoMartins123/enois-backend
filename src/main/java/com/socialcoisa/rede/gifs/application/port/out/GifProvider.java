package com.socialcoisa.rede.gifs.application.port.out;

import java.util.List;

import com.socialcoisa.rede.gifs.application.GifItem;

/**
 * Provedor externo de GIFs. `available()` responde pela chave: sem chave o provedor existe como
 * bean mas nao e consultado, entao nao ha condicional por propriedade em lugar nenhum.
 */
public interface GifProvider {

	String name();

	boolean available();

	/** Termo vazio = em alta. Lista vazia e resposta legitima (nao e erro). */
	List<GifItem> search(String query, int limit);
}
