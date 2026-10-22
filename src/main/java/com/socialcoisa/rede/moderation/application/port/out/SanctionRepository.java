package com.socialcoisa.rede.moderation.application.port.out;

import java.util.List;
import java.util.Optional;

import com.socialcoisa.rede.moderation.domain.UserSanction;

public interface SanctionRepository {

	UserSanction save(UserSanction sanction);

	Optional<UserSanction> findById(String id);

	/** Sancao ativa do usuario (restricao ou ban) — no maximo uma por conta. */
	Optional<UserSanction> findActiveByUser(String userId);

	List<UserSanction> findByUser(String userId, int limit);
}
