package com.socialcoisa.rede.marketplace.application;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.socialcoisa.rede.marketplace.application.port.out.MarketplaceItemRepository;
import com.socialcoisa.rede.marketplace.domain.MarketplaceListing;
import com.socialcoisa.rede.notifications.application.port.out.SystemAnnouncementRepository;
import com.socialcoisa.rede.themes.application.port.out.ThemeGalleryRepository;

/**
 * Limpeza única dos resquícios do ataque no marketplace.
 *
 * <p>Listagens cujo item não existe mais (tema apagado) ou cujo vendedor não é o autor do tema
 * são removidas, com reembolso do que estiver retido no escrow. Depois, todos os temas publicados
 * na galeria entram à venda pelo piso de 50 faíscas — é o que substitui a aba "Temas da
 * comunidade". Roda uma vez (marcador em `system_announcements`); reiniciar não repete.</p>
 */
@Component
public class MarketplaceSanitizeWorker {

	private static final Logger LOG = LoggerFactory.getLogger(MarketplaceSanitizeWorker.class);

	public static final String KEY = "marketplace-sanitize-2026-10-21";

	private final MarketplaceService marketplace;
	private final ThemeGalleryRepository themes;
	private final MarketplaceItemRepository items;
	private final SystemAnnouncementRepository announcements;
	private final MarketplaceGalleryImportWorker galleryImport;
	private final Clock clock;

	public MarketplaceSanitizeWorker(MarketplaceService marketplace, ThemeGalleryRepository themes,
			MarketplaceItemRepository items, SystemAnnouncementRepository announcements,
			MarketplaceGalleryImportWorker galleryImport, Clock clock) {
		this.marketplace = marketplace;
		this.themes = themes;
		this.items = items;
		this.announcements = announcements;
		this.galleryImport = galleryImport;
		this.clock = clock;
	}

	@Scheduled(initialDelayString = "${rede.marketplace.sanitize-initial-delay-ms:90000}")
	@Transactional
	public void sanitizeOnce() {
		if (announcements.isSent(KEY)) {
			return;
		}
		int removed = 0;
		for (MarketplaceListing listing : marketplace.allListings(500)) {
			if (listing.status() != MarketplaceListing.Status.ACTIVE) {
				continue;
			}
			try {
				if (isOrphan(listing) && marketplace.sanitizeListing(listing)) {
					removed++;
				}
			}
			catch (RuntimeException perListing) {
				LOG.warn("Falha ao limpar a listagem {}: {}", listing.id(), perListing.getMessage());
			}
		}
		// Tudo o que está publicado na galeria volta à venda pelo piso de 50 faíscas.
		galleryImport.importPublishedThemes();
		announcements.markSent(KEY, clock.instant());
		LOG.info("Marketplace: {} listagem(ns) de resquício removidas; temas da comunidade à venda.", removed);
	}

	private boolean isOrphan(MarketplaceListing listing) {
		if (listing.itemKind() == MarketplaceListing.ItemKind.THEME) {
			return themes.findById(listing.itemId())
				.map(theme -> !theme.authorId().equals(listing.sellerId()))
				.orElse(true);
		}
		return items.findById(listing.itemId()).isEmpty();
	}
}
