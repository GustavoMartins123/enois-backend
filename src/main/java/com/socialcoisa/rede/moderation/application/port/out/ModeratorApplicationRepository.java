package com.socialcoisa.rede.moderation.application.port.out;

import java.util.List;
import java.util.Optional;

import com.socialcoisa.rede.moderation.domain.ModeratorApplication;
import com.socialcoisa.rede.moderation.domain.ModeratorApplicationStatus;

public interface ModeratorApplicationRepository {

	ModeratorApplication save(ModeratorApplication application);

	Optional<ModeratorApplication> findById(String id);

	/** A candidatura mais recente do usuario (qualquer estado) — o front mostra o estado dela. */
	Optional<ModeratorApplication> findLatestByUser(String userId);

	/** A aprovacao vigente: enquanto existir, o usuario e moderador. */
	Optional<ModeratorApplication> findApprovedByUser(String userId);

	boolean existsPendingByUser(String userId);

	List<ModeratorApplication> findByStatus(ModeratorApplicationStatus status, int limit);

	long countByStatus(ModeratorApplicationStatus status);
}
