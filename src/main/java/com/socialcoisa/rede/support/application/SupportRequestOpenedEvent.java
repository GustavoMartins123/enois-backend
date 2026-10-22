package com.socialcoisa.rede.support.application;

/** Pedido de suporte recém-gravado: o espelho no GitHub é criado depois do commit. */
public record SupportRequestOpenedEvent(String requestId) {
}
