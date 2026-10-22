package com.socialcoisa.rede.identity.application.port.out;

import java.time.Instant;
import java.util.Optional;

import com.socialcoisa.rede.identity.domain.OAuthAuthorizationRequest;

public interface OAuthAuthorizationRequestRepository {

	void save(OAuthAuthorizationRequest request);

	Optional<OAuthAuthorizationRequest> findByState(String state);

	/** Consome o pedido (uso unico): o mesmo state nao volta a valer. */
	void deleteByState(String state);

	/** Remove pedidos vencidos — o state e descartavel, nao precisa ficar no banco para sempre. */
	int deleteExpired(Instant now);
}