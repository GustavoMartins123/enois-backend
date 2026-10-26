package com.socialcoisa.rede.cosmetics.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Comparator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.socialcoisa.rede.coin.application.CoinService;
import com.socialcoisa.rede.coin.application.port.out.CoinLedgerRepository;
import com.socialcoisa.rede.coin.domain.CoinEntry;
import com.socialcoisa.rede.profile.domain.UserProfile;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * A loja de cosméticos (plano C3, Fase 3 da economia): itens NATIVOS começam aqui —
 * molduras de avatar com preço baixo em faíscas. A compra é direta (não é escrow: é
 * item da própria plataforma) e fica auditada no ledger; a posse é o lançamento.
 * Nunca: alcance, impulsionamento — só aparência.
 */
@Service
public class CosmeticsService {

	/** O catálogo fechado da v1: molduras de avatar ("preço baixo", plano C3). */
        public record CosmeticItem(int releaseOrder, String id, String name, String description, long priceMf, String cssRing) {
                public CosmeticItem {
                        if (releaseOrder <= 0) {
                                throw new IllegalArgumentException("A sequência de lançamento deve ser positiva.");
                        }
                }
	}

        // Sequência de entrada no catálogo, independente da posição nesta lista.
	private static final List<CosmeticItem> CATALOG = List.of(
                new CosmeticItem(1, "moldura-ambar", "Moldura Âmbar", "Um anel quente de fim de tarde.", 2_000,
			"ring-2 ring-amber-400"),
                new CosmeticItem(2, "moldura-esmeralda", "Moldura Esmeralda", "Verde de vitrine.", 2_000,
			"ring-2 ring-emerald-400"),
                new CosmeticItem(3, "moldura-violeta", "Moldura Violeta", "O roxo da marca, no seu rosto.", 5_000,
			"ring-2 ring-violet-500"),
                new CosmeticItem(4, "moldura-coral", "Moldura Coral", "Um aro coral para o seu retrato.", 2_000,
			"ring-2 ring-rose-400"),
                new CosmeticItem(5, "moldura-aurora", "Aurora", "Cores que se transformam ao redor do avatar.", 5_000, ""),
                new CosmeticItem(6, "moldura-pulsar", "Pulsar", "Um brilho violeta que pulsa suavemente.", 5_000, ""),
                new CosmeticItem(7, "moldura-solar", "Solar", "Um brilho quente e vivo.", 5_000, ""),
		// Molduras com efeito: o desenho sai do círculo (raio, fogo...) e vive no front.
                new CosmeticItem(8, "moldura-tempestade", "Tempestade", "Raios que estalam em volta do avatar.", 10_000, ""),
                new CosmeticItem(9, "moldura-inferno", "Inferno", "Labaredas que lambem a borda do retrato.", 10_000, ""),
                new CosmeticItem(10, "moldura-buraco-negro", "Buraco Negro", "Matéria girando rumo ao horizonte de eventos.",
			10_000, ""),
                new CosmeticItem(11, "moldura-orbita", "Órbita", "Cometas que passam por trás do seu rosto.", 10_000, ""),
                new CosmeticItem(12, "moldura-glacial", "Glacial", "Cristais de gelo que cintilam no frio.", 10_000, ""),
                new CosmeticItem(13, "moldura-toxica", "Tóxica", "Gosma radioativa que borbulha e escorre.", 10_000, ""),
                new CosmeticItem(14, "moldura-nebulosa", "Nebulosa", "Nuvens cósmicas e estrelas envolvendo o seu avatar.", 10_000, ""),
                new CosmeticItem(15, "moldura-supernova", "Supernova", "Uma explosão estelar com ondas de luz e partículas.", 10_000, ""),
                new CosmeticItem(18, "moldura-galaxia", "Galáxia", "Braços espirais de gás e poeira, com estrelas e partículas em movimento.", 10_000, ""),
                new CosmeticItem(16, "moldura-portal", "Portal", "Um vórtice de energia sugando faíscas para dentro.", 10_000, ""),
                new CosmeticItem(17, "moldura-holograma", "Holograma", "Sinal holográfico com glitch e linhas de varredura.", 10_000,
			""));

	private final CoinService coins;
	private final CoinLedgerRepository ledger;
	private final com.socialcoisa.rede.profile.application.port.out.UserProfileRepository profiles;
	private final Clock clock;

	public CosmeticsService(CoinService coins, CoinLedgerRepository ledger,
			com.socialcoisa.rede.profile.application.port.out.UserProfileRepository profiles, Clock clock) {
		this.coins = coins;
		this.ledger = ledger;
		this.profiles = profiles;
		this.clock = clock;
	}

        static {
                if (CATALOG.stream().map(CosmeticItem::releaseOrder).distinct().count() != CATALOG.size()) {
                        throw new IllegalStateException("Sequência de lançamento duplicada no catálogo.");
                }
        }

        public List<CosmeticItem> catalog(String order) {
                Comparator<CosmeticItem> comparator = switch (order) {
                        case "oldest" -> Comparator.comparingInt(CosmeticItem::releaseOrder);
                        case "newest" -> Comparator.comparingInt(CosmeticItem::releaseOrder).reversed();
                        default -> throw new ApiException(400, "INVALID_CATALOG_ORDER",
                                        "Escolha a ordem oldest ou newest.");
                };
                return CATALOG.stream().sorted(comparator).toList();
	}

	/** A pessoa já comprou a moldura? A posse é o lançamento do ledger (ref por usuário+item). */
	public boolean owns(String userId, String itemId) {
		return ledger.existsByKindAndRefId(CoinEntry.Kind.PURCHASE, ownershipRef(userId, itemId));
	}

	/** Compra direta: debita o comprador e credita a tesouraria — supply fixo preservado. */
	@Transactional
	public CosmeticItem buy(String userId, String itemId) {
		CosmeticItem item = item(itemId);
		String ref = ownershipRef(userId, itemId);
		if (ledger.existsByKindAndRefId(CoinEntry.Kind.PURCHASE, ref)) {
			throw new ApiException(409, "ALREADY_OWNED", "Você já tem esta moldura.");
		}
		Instant now = Instant.now(clock);
		coins.purchase(userId, item.priceMf(), ref, "Loja: " + item.name());
		return item;
	}

	/** Equipa uma moldura já comprada (ou desequipa, com "none"). */
	@Transactional
	public UserProfile equip(String userId, String itemId) {
		if (!"none".equals(itemId)) {
			if (item(itemId) == null || !owns(userId, itemId)) {
				throw new ApiException(403, "NOT_OWNED", "Você precisa comprar a moldura antes de equipar.");
			}
		}
		UserProfile profile = profiles.findById(userId)
			.orElseThrow(() -> new ApiException(404, "USER_NOT_FOUND", "Perfil não encontrado."));
		profile.updateAvatarFrame("none".equals(itemId) ? null : itemId, Instant.now(clock));
		return profiles.save(profile);
	}

	/** A moldura equipada (para o front renderizar o anel). */
	public String equippedFrame(String userId) {
		return profiles.findById(userId).map(UserProfile::avatarFrame).orElse(null);
	}

	private CosmeticItem item(String itemId) {
		return CATALOG.stream().filter(i -> i.id().equals(itemId)).findFirst()
			.orElseThrow(() -> new ApiException(404, "ITEM_NOT_FOUND", "Item não encontrado."));
	}

	private String ownershipRef(String userId, String itemId) {
		return "cosmetico:" + userId + ":" + itemId;
	}
}
