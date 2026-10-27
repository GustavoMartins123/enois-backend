package com.socialcoisa.rede.profile.application.port.out;

/**
 * Notas de rodapé negadas pela moderação. Vive no perfil porque o componente de integridade da
 * credibilidade desconta por elas — a implementação vem do módulo do feed, onde a nota vive.
 *
 * <p>Enviar nota aprovada (ou aguardando análise) não muda a credibilidade: a comunidade se
 * corrigindo é o objetivo. Nota negada é informação falsa e pesa.</p>
 */
public interface NotePenaltyReader {

	int countDeniedByUser(String userId);
}