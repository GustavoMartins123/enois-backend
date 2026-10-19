package com.socialcoisa.rede.jobs.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.socialcoisa.rede.identity.application.IdentityVerificationService;
import com.socialcoisa.rede.profile.application.ReputationService;

/**
 * Politica central de publicacao de vagas: um unico lugar decide se a conta pode publicar, para
 * nenhum caminho alternativo furar a regra. Regra: reputacao >= 150 E (quando a identidade esta
 * sendo exigida) identidade verificada.
 *
 * A exigencia de identidade fica atras de `rede.identity.enforcement-enabled` (padrao false):
 * sem um provedor de identidade autorizado, exigi-la bloquearia TODA publicacao em producao. A
 * decisao continua exposta (para a UI mostrar o que falta) mesmo com a exigencia desligada.
 */
@Service
public class JobPublishingPolicy {

	public static final int REQUIRED_REPUTATION = 150;

	private final ReputationService reputationService;
	private final IdentityVerificationService identityService;
	private final boolean identityEnforced;

	public JobPublishingPolicy(ReputationService reputationService, IdentityVerificationService identityService,
			@Value("${rede.identity.enforcement-enabled:false}") boolean identityEnforced) {
		this.reputationService = reputationService;
		this.identityService = identityService;
		this.identityEnforced = identityEnforced;
	}

	public Decision evaluate(String userId) {
		int reputation = reputationService.reputationOf(userId);
		// "Mais de 150", igual ao JobService.create e ao front — exatamente 150 ainda não publica.
		boolean reputationOk = reputation > REQUIRED_REPUTATION;
		boolean identityVerified = identityService.isVerified(userId);
		boolean allowed = reputationOk && (!identityEnforced || identityVerified);
		String reason = !reputationOk ? "LOW_REPUTATION" : (!allowed ? "IDENTITY_REQUIRED" : null);
		return new Decision(allowed, reputation, REQUIRED_REPUTATION, identityVerified, identityEnforced, reason);
	}

	public record Decision(boolean allowed, int reputation, int requiredReputation, boolean identityVerified,
			boolean identityEnforced, String reason) {
	}
}
