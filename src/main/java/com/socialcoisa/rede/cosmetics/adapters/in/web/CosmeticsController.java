package com.socialcoisa.rede.cosmetics.adapters.in.web;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.socialcoisa.rede.cosmetics.application.CosmeticsService;
import com.socialcoisa.rede.shared.web.CurrentUser;
import com.socialcoisa.rede.shared.web.CurrentUserResolver;

/**
 * A loja de cosméticos (plano C3): molduras de avatar nativas, preço baixo em faíscas,
 * compra direta (auditada no ledger) e equipar. Só aparência — nunca alcance.
 */
@RestController
class CosmeticsController {

	private final CosmeticsService cosmetics;
	private final CurrentUserResolver currentUserResolver;

	CosmeticsController(CosmeticsService cosmetics, CurrentUserResolver currentUserResolver) {
		this.cosmetics = cosmetics;
		this.currentUserResolver = currentUserResolver;
	}

	/** O catálogo da loja; com sessão, marca o que a pessoa já tem e o que está equipado. */
	@GetMapping("/loja")
	List<Map<String, Object>> catalog(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
                        @RequestParam(required = false) String order) {
                String me = sessionToken == null ? null : currentUserResolver.resolve(sessionToken).id().value();
                return cosmetics.catalog(order == null ? "oldest" : order).stream().map(item -> {
			var view = new java.util.LinkedHashMap<String, Object>();
			view.put("id", item.id());
			view.put("name", item.name());
			view.put("description", item.description());
			view.put("priceMf", item.priceMf());
			view.put("cssRing", item.cssRing());
                        view.put("releaseOrder", item.releaseOrder());
			if (me != null) {
				view.put("owned", cosmetics.owns(me, item.id()));
				view.put("equipped", item.id().equals(cosmetics.equippedFrame(me)));
			}
			return (Map<String, Object>) view;
		}).toList();
	}

	@PostMapping("/loja/{itemId}/comprar")
	Map<String, Object> buy(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String itemId) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		var item = cosmetics.buy(user.id().value(), itemId);
		return Map.of("id", item.id(), "owned", true);
	}

	@PostMapping("/loja/{itemId}/equipar")
	Map<String, Object> equip(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String itemId) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		cosmetics.equip(user.id().value(), itemId);
		return Map.of("id", itemId, "equipped", !"none".equals(itemId));
	}

}
