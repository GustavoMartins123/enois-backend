package com.socialcoisa.rede.network.application.port.out;

import java.util.List;
import java.util.Set;

import com.socialcoisa.rede.network.domain.UserBlock;

public interface UserBlockRepository {

	void save(UserBlock block);

	void delete(String blockerId, String blockedId);

	boolean exists(String blockerId, String blockedId);

	/** Ha bloqueio entre os dois, em qualquer direcao. */
	boolean hiddenBetween(String userA, String userB);

	List<UserBlock> findByBlockerId(String blockerId);

	/** Ids que este usuario bloqueou. */
	Set<String> findBlockedIds(String blockerId);

	/**
	 * Ids que nao devem aparecer para este usuario, nas duas direcoes: quem ele bloqueou e quem o
	 * bloqueou. Bloquear esconde o conteudo dos dois lados.
	 */
	Set<String> findHiddenIds(String userId);
}