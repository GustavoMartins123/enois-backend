package com.socialcoisa.rede.moderation.adapters.out.persistence;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.socialcoisa.rede.moderation.application.port.out.SanctionRepository;
import com.socialcoisa.rede.moderation.domain.UserSanction;
import com.socialcoisa.rede.shared.sanctions.SanctionGateReader;

/** Implementa a porta compartilhada de enforcement com o estado real das sancos. */
@Component
class SanctionGateAdapter implements SanctionGateReader {

	private final SanctionRepository sanctions;

	SanctionGateAdapter(SanctionRepository sanctions) {
		this.sanctions = sanctions;
	}

	@Override
	public boolean restricted(String userId) {
		return sanctions.findActiveByUser(userId).isPresent();
	}

	@Override
	public boolean banned(String userId) {
		return sanctions.findActiveByUser(userId).map(UserSanction::isBlocked).orElse(false);
	}

	@Override
	public Optional<String> activeReason(String userId) {
		return sanctions.findActiveByUser(userId).map(UserSanction::reason);
	}
}
