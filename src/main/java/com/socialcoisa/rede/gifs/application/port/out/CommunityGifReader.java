package com.socialcoisa.rede.gifs.application.port.out;

import java.util.List;

/**
 * GIFs que a PRÓPRIA rede já compartilhou (comentários com URL de GIF citando o
 * termo). É o caminho sem chave: o seletor funciona mesmo sem provedor externo.
 */
public interface CommunityGifReader {

	List<String> search(String term, int limit);
}
