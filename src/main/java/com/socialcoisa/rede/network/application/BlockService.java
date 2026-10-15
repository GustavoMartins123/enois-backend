package com.socialcoisa.rede.network.application;

import java.time.Clock;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.socialcoisa.rede.network.application.port.out.ConnectionRepository;
import com.socialcoisa.rede.network.application.port.out.UserBlockRepository;
import com.socialcoisa.rede.network.domain.ConnectionStatus;
import com.socialcoisa.rede.network.domain.UserBlock;
import com.socialcoisa.rede.profile.application.port.out.UserProfileRepository;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Bloqueio definitivo: o usuario bloqueado desaparece do feed, dos comentarios, das notificacoes,
 * da busca e das sugestoes de quem bloqueou — e vice-versa.
 */
@Service
public class BlockService {

	private final UserBlockRepository blockRepository;
	private final ConnectionRepository connectionRepository;
	private final UserProfileRepository userProfileRepository;
	private final Clock clock;

	public BlockService(UserBlockRepository blockRepository, ConnectionRepository connectionRepository,
			UserProfileRepository userProfileRepository, Clock clock) {
		this.blockRepository = blockRepository;
		this.connectionRepository = connectionRepository;
		this.userProfileRepository = userProfileRepository;
		this.clock = clock;
	}

	@Transactional
	public void block(String blockerId, String targetUserId) {
		if (blockerId.equals(targetUserId)) {
			throw new ApiException(400, "SELF_BLOCK", "Nao e possivel bloquear a si mesmo.");
		}
		if (userProfileRepository.findById(targetUserId).isEmpty()) {
			throw new ApiException(404, "USER_NOT_FOUND", "Usuario nao encontrado.");
		}
		blockRepository.save(UserBlock.create(blockerId, targetUserId, clock.instant()));
		// Um bloqueio nao pode continuar aparecendo como conexao ativa.
		connectionRepository.findBetweenUsers(blockerId, targetUserId).ifPresent(connection -> {
			if (connection.status() == ConnectionStatus.PENDING || connection.status() == ConnectionStatus.ACCEPTED) {
				connectionRepository.save(connection.remove(clock.instant()));
			}
		});
	}

	@Transactional
	public void unblock(String blockerId, String targetUserId) {
		blockRepository.delete(blockerId, targetUserId);
	}

	/** Ids bloqueados por este usuario (na ordem em que foram bloqueados). */
	public List<UserBlock> listBlocked(String blockerId) {
		return blockRepository.findByBlockerId(blockerId);
	}

	public boolean isBlockedBy(String blockerId, String targetUserId) {
		return blockRepository.exists(blockerId, targetUserId);
	}

	/** Ids escondidos para o visitante, nas duas direcoes. */
	public Set<String> hiddenIdsFor(String viewerId) {
		if (viewerId == null || viewerId.isBlank()) {
			return Set.of();
		}
		return blockRepository.findHiddenIds(viewerId);
	}
}
