package com.socialcoisa.rede.linkedin.application.port.out;

/**
 * Publica no LinkedIn em nome do membro (produto "Share on LinkedIn", escopo w_member_social).
 * Devolve true quando o LinkedIn aceitou (201). Falha nunca derruba a publicacao do Enois.
 */
public interface LinkedInShareClient {

	boolean share(String accessToken, String memberSub, String commentary, String url, String title);
}
