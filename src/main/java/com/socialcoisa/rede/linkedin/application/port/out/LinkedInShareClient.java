package com.socialcoisa.rede.linkedin.application.port.out;

/**
 * Publica no LinkedIn em nome do membro (produto "Share on LinkedIn", escopo w_member_social).
 * Falha nunca derruba a publicacao do Enois; o resultado (ok/motivo curto) alimenta o painel,
 * para a pessoa ver o que aconteceu sem precisar do log do pod.
 */
public interface LinkedInShareClient {

	/** `detail` curto e estavel: "HTTP_403", "HTTP_422", "EXCEPTION", "SEM_TOKEN"; ok => null. */
	record ShareOutcome(boolean accepted, String detail) {

		public static ShareOutcome ok() {
			return new ShareOutcome(true, null);
		}

		public static ShareOutcome failed(String detail) {
			return new ShareOutcome(false, detail);
		}
	}

	ShareOutcome share(String accessToken, String memberSub, String commentary, String url, String title);
}
