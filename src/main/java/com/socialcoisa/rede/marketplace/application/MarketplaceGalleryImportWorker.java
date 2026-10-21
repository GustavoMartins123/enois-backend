package com.socialcoisa.rede.marketplace.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.socialcoisa.rede.themes.application.port.out.ThemeGalleryRepository;

/**
 * Migração dos temas da comunidade para o marketplace.
 *
 * <p>A aba "Temas da comunidade" deixou de existir: todo tema publicado na galeria passa a ser
 * uma listagem do marketplace, no preço mínimo (50 faíscas), com o autor como vendedor. É
 * idempotente (só cria quando não existe listagem ativa), então roda no boot e de hora em hora
 * sem duplicar nada — inclusive para temas publicados antes desta mudança.</p>
 */
@Component
public class MarketplaceGalleryImportWorker {

	private static final Logger LOG = LoggerFactory.getLogger(MarketplaceGalleryImportWorker.class);

	private static final int MAX_THEMES = 500;

	private final ThemeGalleryRepository themes;
	private final MarketplaceService marketplace;

	public MarketplaceGalleryImportWorker(ThemeGalleryRepository themes, MarketplaceService marketplace) {
		this.themes = themes;
		this.marketplace = marketplace;
	}

	@Scheduled(initialDelayString = "${rede.marketplace.gallery-import-initial-delay-ms:90000}",
		fixedDelayString = "${rede.marketplace.gallery-import-delay-ms:3600000}")
	public void importPublishedThemes() {
		int created = 0;
		for (var theme : themes.findPublished(MAX_THEMES)) {
			try {
				boolean listed = marketplace.ensureListed(theme.authorId(), "THEME", theme.id(), theme.name(),
					theme.description(), MarketplaceService.MIN_PRICE).isPresent();
				if (listed) {
					created++;
				}
			}
			catch (RuntimeException perTheme) {
				// Um pacote problemático não pode impedir a migração dos demais.
				LOG.warn("Falha ao levar o tema {} para o marketplace: {}", theme.id(), perTheme.getMessage());
			}
		}
		if (created > 0) {
			LOG.info("Marketplace: {} tema(s) da comunidade migrados para listagem.", created);
		}
	}
}
