package com.socialcoisa.rede.identity.adapters.out.provider;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.socialcoisa.rede.identity.application.port.out.IdentityVerificationProvider;

/**
 * Registro de provedores de identidade. Em producao hoje NAO ha provedor autorizado, entao a lista
 * vem vazia e a verificacao fica indisponivel (fail-safe). Um provedor novo (gov.br ou privado
 * legitimo) entra apenas como mais um bean de {@link IdentityVerificationProvider}.
 */
@Component
public class IdentityVerificationProviderRegistry {

	private final List<IdentityVerificationProvider> providers;

	public IdentityVerificationProviderRegistry(List<IdentityVerificationProvider> providers) {
		this.providers = providers;
	}

	public Optional<IdentityVerificationProvider> active() {
		return providers.stream().filter(IdentityVerificationProvider::available).findFirst();
	}

	public boolean isAvailable() {
		return active().isPresent();
	}
}
