package com.socialcoisa.rede.corporate.application.port.out;

import java.util.Optional;

import com.socialcoisa.rede.corporate.domain.CorporateProfile;

/**
 * Persistência do perfil profissional. A unicidade do @ profissional é garantida no banco
 * (índice único em username) — a checagem na aplicação é apenas para mensagem amigável.
 */
public interface CorporateProfileRepository {

	CorporateProfile save(CorporateProfile profile);

	Optional<CorporateProfile> findByUserId(String userId);

	Optional<CorporateProfile> findByUsername(String username);

	boolean existsByUsername(String username);
}
