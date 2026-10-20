package com.socialcoisa.rede.services.domain;

/**
 * Ciclo de vida do serviço — a mesma mecânica dos jogos: entra em moderação, a moderação
 * publica ou recusa, e SUSPENDED é a moderação tirando do ar depois (sem apagar).
 */
public enum ServiceStatus {
	SUBMITTED,
	PUBLISHED,
	REJECTED,
	SUSPENDED
}
