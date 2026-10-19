package com.socialcoisa.rede.media.adapters.out.persistence;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.socialcoisa.rede.media.application.port.out.MediaUrlReferenceUpdater;

import jakarta.persistence.EntityManager;

/**
 * Troca e varredura das referências de imagem guardadas como URL direta nas entidades.
 *
 * <p>O registro abaixo é a lista FECHADA das colunas suportadas — nada fora dele é tocado. O
 * UPDATE é condicional (`where coluna = :old`), então a troca é CAS por natureza: quem mudou a
 * foto no meio da migração não é sobrescrito, e URLs que não são do ImageKit nunca entram.</p>
 */
@Repository
class UrlReferencePersistenceAdapter implements MediaUrlReferenceUpdater {

	/** (tabela, coluna) com URL de imagem mantida pela aplicação. */
	private static final List<String[]> REFERENCES = List.of(
		new String[] { "users", "avatar" },
		new String[] { "users", "banner" },
		new String[] { "communities", "avatar" },
		new String[] { "communities", "cover" },
		new String[] { "groups", "avatar" },
		new String[] { "groups", "banner" },
		new String[] { "companies", "logo" },
		new String[] { "companies", "cover" },
		new String[] { "jobs", "logo" },
		new String[] { "events", "cover" },
		new String[] { "games", "cover" },
		new String[] { "developer_profiles", "avatar" },
		new String[] { "developer_profiles", "cover" });

	private final EntityManager em;

	UrlReferencePersistenceAdapter(EntityManager em) {
		this.em = em;
	}

	@Override
	@Transactional
	public int updateReferences(String oldUrl, String newUrl) {
		int updated = 0;
		for (String[] reference : REFERENCES) {
			updated += em.createNativeQuery(
				"update " + reference[0] + " set " + reference[1] + " = :new where " + reference[1] + " = :old")
				.setParameter("new", newUrl)
				.setParameter("old", oldUrl)
				.executeUpdate();
		}
		return updated;
	}

	@Override
	public List<String> findLegacyUrls(int limit) {
		List<String> selects = new ArrayList<>();
		for (String[] reference : REFERENCES) {
			selects.add("select " + reference[1] + " as url from " + reference[0] + " where " + reference[1]
				+ " is not null and (" + reference[1] + " like '%ik.imagekit.io%' or " + reference[1]
				+ " like '/uploads/%')");
		}
		@SuppressWarnings("unchecked")
		List<String> urls = em.createNativeQuery("select url from (" + String.join(" union ", selects)
			+ ") legadas limit " + Math.max(1, limit))
			.getResultList();
		return urls;
	}
}
