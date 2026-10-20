package com.socialcoisa.rede.feed.domain;

/**
 * A identidade que assina a publicação. Uma mesma conta tem duas identidades (social e
 * profissional), e é o backend que decide — nunca o navegador. Toda publicação tem uma:
 * posts antigos e novas publicações sociais são SOCIAL; BUSINESS é sempre CORPORATE.
 */
public enum AuthorIdentity {
	SOCIAL,
	CORPORATE
}
