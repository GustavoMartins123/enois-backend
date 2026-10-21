package com.socialcoisa.rede.support.application.port.out;

import java.util.Optional;

/** Configuração do espelho do suporte (valores já cifrados), guardada no banco. */
public interface SupportSettingsRepository {

	Optional<String> findCipher(String name);

	void saveCipher(String name, String cipher, java.time.Instant now);

	void delete(String name);
}
