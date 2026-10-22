package com.socialcoisa.rede.groups.domain;

/** Situação de um pedido de ingresso. Decidido não volta a pendente: pedir de novo cria outro. */
public enum JoinRequestStatus {
	PENDING, APPROVED, REJECTED, CANCELLED
}
