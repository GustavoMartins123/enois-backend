package com.socialcoisa.rede.cosmetics.application.port.out;

import java.util.List;
import java.util.Optional;

import com.socialcoisa.rede.cosmetics.domain.CommunityCosmetic;
import com.socialcoisa.rede.cosmetics.domain.CommunityCosmeticStatus;

public interface CommunityCosmeticRepository {

	CommunityCosmetic save(CommunityCosmetic cosmetic);

	Optional<CommunityCosmetic> findById(String id);

	List<CommunityCosmetic> findByCreator(String creatorId, int limit);

	List<CommunityCosmetic> findByStatus(CommunityCosmeticStatus status, int limit);
}
