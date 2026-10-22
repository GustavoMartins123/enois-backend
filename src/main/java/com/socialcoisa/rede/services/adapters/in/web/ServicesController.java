package com.socialcoisa.rede.services.adapters.in.web;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.socialcoisa.rede.services.application.ServicesService;
import com.socialcoisa.rede.services.domain.ServiceModels.ServiceContract;
import com.socialcoisa.rede.services.domain.ServiceModels.ServiceListing;
import com.socialcoisa.rede.services.domain.ServiceModels.ServiceProposal;
import com.socialcoisa.rede.services.domain.ServiceModels.ServiceRequest;
import com.socialcoisa.rede.shared.web.CurrentUser;
import com.socialcoisa.rede.shared.web.CurrentUserResolver;

/**
 * Serviços freelance (Fase 5): /servicos (catálogo de ofertas e pedidos abertos),
 * /servicos/ofertas (publicar/contratar), /servicos/pedidos (publicar, propostas,
 * escolher) e /servicos/contratos (o painel do escrow: entregar, aceitar, reembolso).
 */
@RestController
class ServicesController {

	private final ServicesService services;
	private final CurrentUserResolver currentUserResolver;

	ServicesController(ServicesService services, CurrentUserResolver currentUserResolver) {
		this.services = services;
		this.currentUserResolver = currentUserResolver;
	}

	@GetMapping("/servicos")
	Map<String, Object> catalog(@RequestParam(defaultValue = "40") int listings,
			@RequestParam(defaultValue = "40") int requests) {
		return Map.of(
			"ofertas", services.listingCatalog(listings).stream().map(ListingView::from).toList(),
			"pedidos", services.requestCatalog(requests).stream().map(RequestView::from).toList());
	}

	@GetMapping("/servicos/minhas")
	Map<String, Object> mine(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {
		CurrentUser user = user(userId, sessionToken);
		return Map.of(
			"ofertas", services.listingsBySeller(user.id().value()).stream().map(ListingView::from).toList(),
			"contratosComoComprador", services.contractsByBuyer(user.id().value()).stream().map(ContractView::from).toList(),
			"contratosComoVendedor", services.contractsBySeller(user.id().value()).stream().map(ContractView::from).toList());
	}

	@PostMapping("/servicos/ofertas")
	Map<String, Object> publishListing(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestBody PublishListingRequest request) {
		CurrentUser user = user(userId, sessionToken);
		ServiceListing listing = services.publishListing(user.id().value(), request.category(), request.title(),
			request.description(), request.deliverables(), request.price(), request.currency(),
			request.deliveryDays());
		return Map.of("id", listing.id(), "status", listing.status().name());
	}

	@PostMapping("/servicos/ofertas/{listingId}/contratar")
	Map<String, Object> buyListing(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String listingId) {
		CurrentUser user = user(userId, sessionToken);
		ServiceContract contract = services.buyListing(listingId, user.id().value());
		return Map.of("contractId", contract.id(), "state", contract.escrowState().name());
	}

	@PostMapping("/servicos/pedidos")
	Map<String, Object> createRequest(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestBody CreateRequestRequest request) {
		CurrentUser user = user(userId, sessionToken);
		ServiceRequest created = services.createRequest(user.id().value(), request.category(), request.title(),
			request.description(), request.deliverables(), request.budgetMin(), request.budgetMax(),
			request.deadline());
		return Map.of("id", created.id(), "status", created.status().name());
	}

	@PostMapping("/servicos/pedidos/{requestId}/propostas")
	Map<String, Object> propose(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String requestId, @RequestBody ProposeRequest request) {
		CurrentUser user = user(userId, sessionToken);
		ServiceProposal proposal = services.propose(requestId, user.id().value(), request.message(), request.price());
		return Map.of("id", proposal.id(), "status", proposal.status().name());
	}

	@GetMapping("/servicos/pedidos/{requestId}/propostas")
	List<ProposalView> proposals(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String requestId) {
		CurrentUser user = user(userId, sessionToken);
		return services.proposalsFor(requestId, user.id().value()).stream().map(ProposalView::from).toList();
	}

	@PostMapping("/servicos/pedidos/{requestId}/escolher")
	Map<String, Object> choose(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String requestId, @RequestBody ChooseRequest request) {
		CurrentUser user = user(userId, sessionToken);
		ServiceContract contract = services.choose(requestId, user.id().value(), request.proposalId());
		return Map.of("contractId", contract.id(), "state", contract.escrowState().name());
	}

	@PostMapping("/servicos/contratos/{contractId}/entregar")
	Map<String, Object> deliver(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String contractId, @RequestBody DeliverRequest request) {
		CurrentUser user = user(userId, sessionToken);
		ServiceContract contract = services.deliver(contractId, user.id().value(), request.note());
		return Map.of("contractId", contract.id(), "state", contract.escrowState().name());
	}

	@PostMapping("/servicos/contratos/{contractId}/aceitar")
	Map<String, Object> accept(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String contractId) {
		CurrentUser user = user(userId, sessionToken);
		ServiceContract contract = services.accept(contractId, user.id().value());
		return Map.of("contractId", contract.id(), "state", contract.escrowState().name());
	}

	@PostMapping("/servicos/contratos/{contractId}/reembolso")
	Map<String, Object> refund(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String contractId) {
		CurrentUser user = user(userId, sessionToken);
		ServiceContract contract = services.refund(contractId, user.id().value());
		return Map.of("contractId", contract.id(), "state", contract.escrowState().name());
	}

	private CurrentUser user(String userId, String sessionToken) {
		return currentUserResolver.resolve(userId, sessionToken);
	}

	record PublishListingRequest(String category, String title, String description, String deliverables, long price,
			String currency,
			int deliveryDays) {
	}

	record CreateRequestRequest(String category, String title, String description, String deliverables,
			long budgetMin, long budgetMax, java.time.Instant deadline) {
	}

	record ProposeRequest(String message, long price) {
	}

	record ChooseRequest(String proposalId) {
	}

	record DeliverRequest(String note) {
	}

	record ListingView(String id, String sellerId, String category, String title, String description,
			String deliverables, long price, String currency, int deliveryDays, String status, String createdAt) {
		static ListingView from(ServiceListing l) {
			return new ListingView(l.id(), l.sellerId(), l.category(), l.title(), l.description(), l.deliverables(),
				l.price(), l.currency(), l.deliveryDays(), l.status().name(), l.createdAt().toString());
		}
	}

	record RequestView(String id, String requesterId, String category, String title, String description,
			String deliverables, long budgetMin, long budgetMax, String deadline, String status, String createdAt) {
		static RequestView from(ServiceRequest r) {
			return new RequestView(r.id(), r.requesterId(), r.category(), r.title(), r.description(), r.deliverables(),
				r.budgetMin(), r.budgetMax(), r.deadline() == null ? null : r.deadline().toString(),
				r.status().name(), r.createdAt().toString());
		}
	}

	record ProposalView(String id, String requestId, String userId, String message, long price, String status,
			String createdAt) {
		static ProposalView from(ServiceProposal p) {
			return new ProposalView(p.id(), p.requestId(), p.userId(), p.message(), p.price(), p.status().name(),
				p.createdAt().toString());
		}
	}

	record ContractView(String id, String listingId, String requestId, String buyerId, String sellerId, long price,
			String escrowState, String deliverableNote, String createdAt, String acceptedAt) {
		static ContractView from(ServiceContract c) {
			return new ContractView(c.id(), c.listingId(), c.requestId(), c.buyerId(), c.sellerId(), c.price(),
				c.escrowState().name(), c.deliverableNote(), c.createdAt().toString(),
				c.acceptedAt() == null ? null : c.acceptedAt().toString());
		}
	}
}
