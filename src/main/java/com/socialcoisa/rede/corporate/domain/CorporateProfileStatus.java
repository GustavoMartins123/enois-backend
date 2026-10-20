package com.socialcoisa.rede.corporate.domain;

/**
 * Ciclo de vida do perfil profissional, 1:1 opcional com a conta.
 *
 * DRAFT: em configuração (não aparece como pronto para candidaturas). ACTIVE: ativo — pode
 * assinar BUSINESS e ser mostrado publicamente. DISABLED: desativado pela pessoa — some da
 * apresentação pública, nada é apagado (publicações, candidaturas e histórico permanecem).
 */
public enum CorporateProfileStatus {
	DRAFT,
	ACTIVE,
	DISABLED
}
