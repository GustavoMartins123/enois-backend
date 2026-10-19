package com.socialcoisa.rede.media.application.port.out;

import java.util.List;

/**
 * Referências de imagem guardadas como URL nas entidades (avatar, banner, capas, logos).
 *
 * A troca é CAS por natureza: o UPDATE só toca linhas que AINDA têm a URL antiga — quem trocou a
 * foto no meio do caminho não é sobrescrito. Se a mesma URL aparece em várias linhas, todas são
 * atualizadas de uma vez (referência compartilhada).
 */
public interface MediaUrlReferenceUpdater {

	/** Troca a URL antiga pela do S3 em TODAS as colunas do registro; devolve quantas linhas mudaram. */
	int updateReferences(String oldUrl, String newUrl);

	/** URLs legadas conhecidas (ImageKit ou caminho relativo antigo), para a varredura em lote. */
	List<String> findLegacyUrls(int limit);
}
