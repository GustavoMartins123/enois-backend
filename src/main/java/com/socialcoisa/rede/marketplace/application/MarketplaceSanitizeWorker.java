package com.socialcoisa.rede.marketplace.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.socialcoisa.rede.marketplace.application.port.out.MarketplaceItemRepository;
import com.socialcoisa.rede.marketplace.domain.MarketplaceListing;
import com.socialcoisa.rede.profile.application.port.out.UserProfileRepository;
import com.socialcoisa.rede.shared.sanctions.SanctionGateReader;
import com.socialcoisa.rede.themes.application.port.out.ThemeGalleryRepository;

/**
 * Limpeza única dos resquícios do ataque no marketplace.
 *
 * <p>Listagens cujo item não existe mais (tema apagado), cujo vendedor não é o autor do tema ou
 * cujo vendedor foi BANIDO (contas do ataque) são removidas, com reembolso do que estiver retido
 * no escrow. Depois, todos os temas publicados na galeria entram à venda pelo piso de 50 faíscas —
 * é o que substitui a aba "Temas da comunidade". Idempotente: roda no boot e de hora em hora.</p>
 */
@Component
public class MarketplaceSanitizeWorker {

	private static final Logger LOG = LoggerFactory.getLogger(MarketplaceSanitizeWorker.class);

	private final MarketplaceService marketplace;
	private final ThemeGalleryRepository themes;
	private final MarketplaceItemRepository items;
	private final SanctionGateReader sanctionGate;
	private final UserProfileRepository profiles;
	private final java.util.List<String> suspiciousPatterns;
	private final MarketplaceGalleryImportWorker galleryImport;

	public MarketplaceSanitizeWorker(MarketplaceService marketplace, ThemeGalleryRepository themes,
			MarketplaceItemRepository items, SanctionGateReader sanctionGate, UserProfileRepository profiles,
			@Value("${rede.security.suspicious.name-patterns:probe}") String patterns,
			MarketplaceGalleryImportWorker galleryImport) {
		this.marketplace = marketplace;
		this.themes = themes;
		this.items = items;
		this.sanctionGate = sanctionGate;
		this.profiles = profiles;
		this.suspiciousPatterns = java.util.Arrays.stream(patterns.split(","))
			.map(String::trim)
			.filter(pattern -> !pattern.isEmpty())
			.map(pattern -> pattern.toLowerCase(java.util.Locale.ROOT))
			.toList();
		this.galleryImport = galleryImport;
	}

	@Scheduled(initialDelayString = "${rede.marketplace.sanitize-initial-delay-ms:90000}",
		fixedDelayString = "${rede.marketplace.sanitize-delay-ms:3600000}")
	@Transactional
	public void sanitize() {
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
		if (removed > 0) {
			LOG.info("Marketplace: {} listagem(ns) de resquício removidas; temas da comunidade à venda.",
				removed);
		}
	}

	private boolean hasSuspiciousName(String sellerId) {
		if (suspiciousPatterns.isEmpty()) {
			return false;
		}
		return profiles.findById(sellerId)
			.map(profile -> {
				String typed = (profile.name() + " " + profile.username()).toLowerCase(java.util.Locale.ROOT);
				return suspiciousPatterns.stream().anyMatch(typed::contains);
			})
			.orElse(false);
	}

	private boolean isOrphan(MarketplaceListing listing) {
		// Vendedor banido (contas do ataque): a listagem cai, com reembolso do escrow.
		if (listing.sellerId() != null && sanctionGate.banned(listing.sellerId())) {
			return true;
		}
		// Contas de teste/ataque reconhecidas pelo mesmo padrão da triagem de suspeitos
		// ("probe"): os temas "Probe Escrow" não podem continuar à venda.
		if (listing.sellerId() != null && hasSuspiciousName(listing.sellerId())) {
			return true;
		}
		if (listing.itemKind() == MarketplaceListing.ItemKind.THEME) {
			return themes.findById(listing.itemId())
				.map(theme -> !theme.authorId().equals(listing.sellerId()))
				.orElse(true);
		}
		return items.findById(listing.itemId()).isEmpty();
	}
}
