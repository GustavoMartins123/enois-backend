package com.socialcoisa.rede.games.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.socialcoisa.rede.games.domain.DeveloperProfile;

public interface DeveloperProfileRepository {

	DeveloperProfile save(DeveloperProfile profile);

	Optional<DeveloperProfile> findByUserId(String userId);

	/** Perfis de vários criadores de uma vez (a listagem não faz uma consulta por linha). */
	List<DeveloperProfile> findAllByUserIds(Collection<String> userIds);
}
