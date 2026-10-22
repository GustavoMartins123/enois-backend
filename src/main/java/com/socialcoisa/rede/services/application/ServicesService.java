package com.socialcoisa.rede.services.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.socialcoisa.rede.coin.application.CoinService;
import com.socialcoisa.rede.services.application.port.out.ServiceRepository;
import com.socialcoisa.rede.services.domain.ServiceModels.ServiceContract;
import com.socialcoisa.rede.services.domain.ServiceModels.ServiceListing;
import com.socialcoisa.rede.services.domain.ServiceModels.ServiceProposal;
import com.socialcoisa.rede.services.domain.ServiceModels.ServiceRequest;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Serviços freelance (Fase 5): ofertar (Fiverr) e solicitar (demanda), pagos em
 * faíscas com ESCROW — o contrato nasce HELD (as faíscas retidas), o vendedor entrega
 * e o comprador aceita (libera −10% de taxa) ou recusa (devolve 100%). Antirruído:
 * entregáveis obrigatórios, teto de 5 ofertas abertas por conta.
 */
@Service
public class ServicesService {

	private static final int MAX_OPEN_LISTINGS = 5;

	private final ServiceRepository services;
	private final CoinService coins;
	private final Clock clock;

	public ServicesService(ServiceRepository services, CoinService coins, Clock clock) {
		this.services = services;
		this.coins = coins;
		this.clock = clock;
	}

	// --- D2.2: ofertar (o profissional publica) ---

	@Transactional
	public ServiceListing publishListing(String sellerId, String category, String title, String description,
			String deliverables, long price, String currency, int deliveryDays) {
		if (services.countOpenListingsBySeller(sellerId) >= MAX_OPEN_LISTINGS) {
			throw new ApiException(403, "TOO_MANY_OPEN_SERVICES",
				"Você já tem " + MAX_OPEN_LISTINGS + " serviços abertos — conclua ou remova um antes.");
		}
		Instant now = clock.instant();
		return services.saveListing(ServiceListing.create(sellerId, category, title, description, deliverables, price,
			currency, deliveryDays, now));
	}

	@Transactional
	public ServiceContract buyListing(String listingId, String buyerId) {
		ServiceListing listing = listing(listingId);
		if (listing.status() != ServiceListing.Status.ACTIVE) {
			throw new ApiException(409, "SERVICE_NOT_ACTIVE", "Este serviço não está mais aberto.");
		}
		if (listing.ownedBy(buyerId)) {
			throw new ApiException(400, "OWN_SERVICE", "Não dá para contratar o seu próprio serviço.");
		}
		if ("BRL".equalsIgnoreCase(listing.currency())) {
			// Reais não entram no ledger de faíscas: o pagamento é combinado direto com o profissional.
			throw new ApiException(409, "DIRECT_PAYMENT_REQUIRED",
				"Este serviço é cobrado em reais. Fale com o profissional para combinar o pagamento.");
		}
		Instant now = clock.instant();
		ServiceContract contract = services.saveContract(ServiceContract.fromListing(listing, buyerId, now));
		coins.escrowHold(buyerId, listing.price(), escrowRef(contract), "Contratação de serviço: " + listing.title());
		return contract;
	}

	@Transactional
	public ServiceContract deliver(String contractId, String sellerId, String note) {
		ServiceContract contract = contract(contractId);
		if (!contract.ownedBySeller(sellerId)) {
			throw new ApiException(403, "NOT_THE_SELLER", "Só quem executa entrega o serviço.");
		}
		return services.saveContract(contract.deliver(note, clock.instant()));
	}

	/** O comprador aceita a entrega: libera o escrow para o vendedor (−10% de taxa). */
	@Transactional
	public ServiceContract accept(String contractId, String buyerId) {
		ServiceContract contract = contract(contractId);
		if (!contract.ownedByBuyer(buyerId)) {
			throw new ApiException(403, "NOT_THE_BUYER", "Só quem contratou aceita a entrega.");
		}
		Instant now = clock.instant();
		coins.escrowRelease(contract.sellerId(), contract.price(), escrowRef(contract),
			"Serviço concluído (escrow liberado)");
		return services.saveContract(contract.release(now));
	}

	/** O comprador recusa: devolve 100%. */
	@Transactional
	public ServiceContract refund(String contractId, String buyerId) {
		ServiceContract contract = contract(contractId);
		if (!contract.ownedByBuyer(buyerId)) {
			throw new ApiException(403, "NOT_THE_BUYER", "Só quem contratou recusa a entrega.");
		}
		Instant now = clock.instant();
		coins.escrowRefund(contract.buyerId(), contract.price(), escrowRef(contract),
			"Reembolso de serviço");
		return services.saveContract(contract.refund(now));
	}

	// --- D2.1: solicitar (a demanda publica) ---

	@Transactional
	public ServiceRequest createRequest(String requesterId, String category, String title, String description,
			String deliverables, long budgetMin, long budgetMax, Instant deadline) {
		Instant now = clock.instant();
		return services.saveRequest(ServiceRequest.create(requesterId, category, title, description, deliverables,
			budgetMin, budgetMax, deadline, now));
	}

	@Transactional
	public ServiceProposal propose(String requestId, String userId, String message, long price) {
		ServiceRequest request = request(requestId);
		if (request.status() != ServiceRequest.Status.OPEN) {
			throw new ApiException(409, "REQUEST_NOT_OPEN", "Este pedido não está mais aberto a propostas.");
		}
		if (request.ownedBy(userId)) {
			throw new ApiException(400, "OWN_REQUEST", "Não dá para propor no seu próprio pedido.");
		}
		if (price < request.budgetMin() || price > request.budgetMax()) {
			throw new ApiException(400, "PRICE_OUT_OF_BUDGET",
				"O preço da proposta precisa estar dentro da faixa (" + request.budgetMin() + "–"
					+ request.budgetMax() + ").");
		}
		return services.saveProposal(ServiceProposal.create(requestId, userId, message, price, clock.instant()));
	}

	/** O requester escolhe uma proposta: o escrow nasce retido e o pedido fecha. */
	@Transactional
	public ServiceContract choose(String requestId, String requesterId, String proposalId) {
		ServiceRequest request = request(requestId);
		if (!request.ownedBy(requesterId)) {
			throw new ApiException(403, "NOT_THE_REQUESTER", "Só quem pediu escolhe a proposta.");
		}
		if (request.status() != ServiceRequest.Status.OPEN) {
			throw new ApiException(409, "REQUEST_NOT_OPEN", "Este pedido já fechou.");
		}
		ServiceProposal proposal = services.findProposalById(proposalId)
			.orElseThrow(() -> new ApiException(404, "PROPOSAL_NOT_FOUND", "Proposta não encontrada."));
		if (!proposal.requestId().equals(requestId)) {
			throw new ApiException(400, "PROPOSAL_MISMATCH", "Essa proposta não é deste pedido.");
		}
		Instant now = clock.instant();
		ServiceContract contract = services.saveContract(ServiceContract.fromRequest(request, proposal, requesterId, now));
		coins.escrowHold(requesterId, proposal.price(), escrowRef(contract), "Serviço contratado: " + request.title());
		services.saveProposal(new ServiceProposal(proposal.id(), proposal.requestId(), proposal.userId(),
			proposal.message(), proposal.price(), ServiceProposal.Status.CHOSEN, proposal.createdAt()));
		services.saveRequest(new ServiceRequest(request.id(), request.requesterId(), request.category(), request.title(),
			request.description(), request.deliverables(), request.budgetMin(), request.budgetMax(), request.deadline(),
			ServiceRequest.Status.CHOSEN, request.createdAt(), now));
		return contract;
	}

	public List<ServiceListing> listingCatalog(int limit, String query, String category) {
		return services.findActiveListings(Math.min(200, Math.max(1, limit) * 4)).stream()
			.filter(l -> matches(query, l.title(), l.description(), l.deliverables()))
			.filter(l -> matchesCategory(category, l.category()))
			.limit(Math.min(60, Math.max(1, limit))).toList();
	}

	public List<ServiceListing> listingsBySeller(String sellerId) {
		return services.findListingsBySeller(sellerId);
	}

	public List<ServiceRequest> requestCatalog(int limit, String query, String category) {
		return services.findOpenRequests(Math.min(200, Math.max(1, limit) * 4)).stream()
			.filter(r -> matches(query, r.title(), r.description(), r.deliverables()))
			.filter(r -> matchesCategory(category, r.category()))
			.limit(Math.min(60, Math.max(1, limit))).toList();
	}

	private static boolean matches(String query, String... fields) {
		if (query == null || query.isBlank()) {
			return true;
		}
		String term = query.trim().toLowerCase(java.util.Locale.ROOT);
		for (String field : fields) {
			if (field != null && field.toLowerCase(java.util.Locale.ROOT).contains(term)) {
				return true;
			}
		}
		return false;
	}

	private static boolean matchesCategory(String category, String value) {
		if (category == null || category.isBlank()) {
			return true;
		}
		return value != null && value.equalsIgnoreCase(category.trim());
	}

	public List<ServiceProposal> proposalsFor(String requestId, String requesterId) {
		ServiceRequest request = request(requestId);
		if (!request.ownedBy(requesterId)) {
			throw new ApiException(403, "NOT_THE_REQUESTER", "Só quem pediu vê as propostas.");
		}
		return services.findProposalsByRequest(requestId);
	}

	public List<ServiceContract> contractsByBuyer(String buyerId) {
		return services.findContractsByBuyer(buyerId);
	}

	public List<ServiceContract> contractsBySeller(String sellerId) {
		return services.findContractsBySeller(sellerId);
	}

	private ServiceListing listing(String id) {
		return services.findListingById(id)
			.orElseThrow(() -> new ApiException(404, "SERVICE_NOT_FOUND", "Serviço não encontrado."));
	}

	private ServiceRequest request(String id) {
		return services.findRequestById(id)
			.orElseThrow(() -> new ApiException(404, "REQUEST_NOT_FOUND", "Pedido não encontrado."));
	}

	private ServiceContract contract(String id) {
		return services.findContractById(id)
			.orElseThrow(() -> new ApiException(404, "CONTRACT_NOT_FOUND", "Contrato não encontrado."));
	}

	private String escrowRef(ServiceContract contract) {
		return "svc:" + contract.id();
	}

}
