package com.socialcoisa.rede.moderation.application.port.out;

import java.util.List;
import java.util.Optional;

import com.socialcoisa.rede.moderation.domain.BlockedIp;

/** Estado da lista de IPs bloqueados pela moderacao (V115). */
public interface BlockedIpRepository {

	BlockedIp save(BlockedIp blockedIp);

	Optional<BlockedIp> findByIp(String ip);

	boolean existsByIp(String ip);

	List<BlockedIp> findRecent(int limit);

	void deleteByIp(String ip);
}
