package com.socialcoisa.rede.profile.adapters.in.web;

/** Corpo da atualizacao da identidade exibida; campo nulo/vazio limpa o valor. */
public record UpdateIdentityRequest(String nickname, String socialName, String pronouns) {
}
