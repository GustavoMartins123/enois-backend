package com.socialcoisa.rede.identity.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.socialcoisa.rede.identity.domain.TwoFactorChallenge;

/**
 * Persistencia do segundo fator: desafios do login, segredos do TOTP (cifrados) e codigos de
 * recuperacao. Os segredos nao passam pelo dominio de perfil de proposito.
 */
public interface TwoFactorRepository {

	void saveChallenge(TwoFactorChallenge challenge);

	Optional<TwoFactorChallenge> findChallenge(String id);

	/** Desafio mais recente do usuario por metodo (usado ao confirmar a ativacao por e-mail). */
	Optional<TwoFactorChallenge> findLatestChallenge(String userId, com.socialcoisa.rede.identity.domain.TwoFactorMethod method);

	/** Desafios vencidos sao descartaveis: nao precisam ficar no banco. */
	int deleteExpiredChallenges(Instant now);

	/** Segredo pendente (gerado no setup e ainda nao confirmado por um codigo). */
	void savePendingSecret(String userId, String encryptedSecret, Instant now);

	Optional<String> findPendingSecret(String userId);

	Optional<Instant> findPendingSecretCreatedAt(String userId);

	/** Ativa o TOTP: o segredo pendente passa a ser o valido. */
	void activateTotpSecret(String userId, String encryptedSecret, Instant now);

	/** Segredo ativo (cifrado) para validar um codigo no login. */
	Optional<String> findActiveSecret(String userId);

	void clearSecrets(String userId);

	void saveBackupCodes(String userId, java.util.List<String> codeHashes, Instant now);

	int countUnusedBackupCodes(String userId);

	/** Consome o codigo de recuperacao: uso unico. Devolve false se nao existir/ja foi usado. */
	boolean consumeBackupCode(String userId, String codeHash, Instant now);

	/** Usuarios com segredo TOTP ativo (cifrado) — usado pelo reset emergencial de 2FA quebrado. */
	List<String> findUsersWithActiveTotpSecret();

	void deleteBackupCodes(String userId);
}