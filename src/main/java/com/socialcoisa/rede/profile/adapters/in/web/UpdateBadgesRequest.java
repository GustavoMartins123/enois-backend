package com.socialcoisa.rede.profile.adapters.in.web;

import java.util.List;

/** Corpo da atualizacao dos emblemas (ate 5 URLs do CDN); nulo/vazio limpa. */
public record UpdateBadgesRequest(List<String> badges) {
}
