package com.socialcoisa.rede.coin.adapters.in.web;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.socialcoisa.rede.coin.application.CoinService;
import com.socialcoisa.rede.coin.domain.CoinEntry;
import com.socialcoisa.rede.profile.application.port.out.UserProfileRepository;
import org.springframework.http.ResponseEntity;

import com.socialcoisa.rede.idempotency.application.CommandResponse;
import com.socialcoisa.rede.shared.web.ApiException;
import com.socialcoisa.rede.shared.web.CurrentUser;
import com.socialcoisa.rede.shared.web.CurrentUserResolver;
import com.socialcoisa.rede.idempotency.application.IdempotencyApplicationService;

/** Carteira da faísca: saldo, extrato e transferência P2P estilo Pix (por username). */
@RestController
class CoinController {

	private final CoinService coins;
	private final com.socialcoisa.rede.coin.application.CoinRateService rates;
	private final CurrentUserResolver currentUserResolver;
	private final IdempotencyApplicationService idempotency;
	private final UserProfileRepository profiles;
	private final com.socialcoisa.rede.shared.web.AdminGuard adminGuard;

	CoinController(CoinService coins, com.socialcoisa.rede.coin.application.CoinRateService rates,
			CurrentUserResolver currentUserResolver, IdempotencyApplicationService idempotency,
			UserProfileRepository profiles, com.socialcoisa.rede.shared.web.AdminGuard adminGuard) {
		this.coins = coins;
		this.rates = rates;
		this.currentUserResolver = currentUserResolver;
		this.idempotency = idempotency;
		this.profiles = profiles;
		this.adminGuard = adminGuard;
	}

	/**
	 * A MINI BOLSA (monitor público): a cotação de referência da faísca em BRL e o
	 * histórico que alimenta o gráfico. NÃO é câmbio — não compra faísca com dinheiro.
	 */
	@GetMapping("/coin/cotacao")
	Map<String, Object> quote(@RequestParam(defaultValue = "90") int history) {
		var current = rates.latest();
		return Map.of(
			"brlCents", current.brlCents(),
			"escala", com.socialcoisa.rede.coin.application.CoinService.SCALE,
			"atualizadaEm", current.effectiveAt().toString(),
			"historico", rates.history(history).stream().map(r -> Map.of(
				"brlCents", r.brlCents(),
				"em", r.effectiveAt().toString())).toList());
	}

	/** Publica uma nova cotação de referência (só administração). */
	@PostMapping("/coin/cotacao")
	Map<String, Object> setQuote(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestBody SetQuoteRequest request) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		adminGuard.requireAdmin(user);
		var published = rates.publish(request.brlCents(), request.note());
		return Map.of("brlCents", published.brlCents(), "em", published.effectiveAt().toString());
	}

	record SetQuoteRequest(long brlCents, String note) {
	}

	@GetMapping("/carteira")
	Map<String, Object> wallet(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		com.socialcoisa.rede.coin.application.CoinService.Wallet wallet;
		try {
			wallet = coins.wallet(user.id().value(), usernameOf(user.id().value()));
		} catch (Exception debug) {
			org.slf4j.LoggerFactory.getLogger(CoinController.class).error("DEBUG carteira", debug);
			throw debug;
		}
		return Map.of(
			"balance", wallet.balance(),
			"escala", wallet.escala(),
			"cotacaoBrlCents", wallet.cotacaoBrlCents(),
			"chave", wallet.chave(),
			"entries", wallet.entries().stream().map(EntryView::from).toList());
	}

	@PostMapping("/carteira/transferencias")
	ResponseEntity<String> transfer(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@RequestBody TransferRequest request) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		if (request.toUsername() == null || request.toUsername().isBlank() || request.amount() == null
				|| request.amount() <= 0) {
			throw new ApiException(400, "BAD_REQUEST", "Informe o destinatário e o valor.");
		}
		String toUserId = profiles.findByUsername(request.toUsername().trim().toLowerCase())
			.orElseThrow(() -> new ApiException(404, "USER_NOT_FOUND", "Usuário de destino não encontrado."))
			.id();
		String hash = idempotency.hash(Map.of("to", toUserId, "amount", request.amount(),
			"message", request.message() == null ? "" : request.message()));
		return idempotency.execute(user.id(), "POST", "/api/carteira/transferencias", idempotencyKey, hash,
			() -> {
				CoinEntry out = coins.transfer(user.id().value(), request.toUsername(), request.amount(),
					request.message(), toUserId, request.toUsername());
				return new CommandResponse(200, Map.of("status", "SENT", "amount", request.amount(),
					"toUsername", request.toUsername()));
			});
	}

	record TransferRequest(String toUsername, Long amount, String message) {
	}

	record EntryView(long amount, String kind, String message, String createdAt) {
		static EntryView from(CoinEntry entry) {
			return new EntryView(entry.amount(), entry.kind().name(), entry.message(),
				entry.createdAt().toString());
		}
	}

	private String usernameOf(String userId) {
		return profiles.findById(userId).map(p -> p.username()).orElse(userId);
	}
}
