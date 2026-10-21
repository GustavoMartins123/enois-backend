package com.socialcoisa.rede.age.application.port.out;

import java.util.List;
import java.util.Optional;

import com.socialcoisa.rede.age.domain.ParentalLink;

public interface ParentalLinkRepository {

	void save(ParentalLink link);

	Optional<ParentalLink> findByTokenHash(String tokenHash);

	Optional<ParentalLink> findActiveByMinor(String minorUserId);

	List<ParentalLink> findActiveByGuardian(String guardianUserId);

	/** Cancela convites pendentes do menor (um convite valido por vez). */
	void cancelPendingByMinor(String minorUserId);
}
