package com.socialcoisa.rede.shared.security;

import com.socialcoisa.rede.moderation.domain.ReportReason;
import com.socialcoisa.rede.moderation.domain.TargetType;

/**
 * Evento disparado quando um guard de seguranca detecta comportamento suspeito e quer
 * registrar uma denuncia automatica na moderacao. O listener no modulo de moderacao
 * transforma isso em um {@link com.socialcoisa.rede.moderation.domain.Report}.
 */
public record AutoReportEvent(
		String userId,
		TargetType targetType,
		ReportReason reason,
		String description) {
}
