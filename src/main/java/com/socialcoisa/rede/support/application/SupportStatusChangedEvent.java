package com.socialcoisa.rede.support.application;

import com.socialcoisa.rede.support.domain.SupportStatus;

/** Status do ticket mudou no painel: o espelho no GitHub é sincronizado depois do commit. */
public record SupportStatusChangedEvent(String requestId, SupportStatus previous, SupportStatus current) {
}
