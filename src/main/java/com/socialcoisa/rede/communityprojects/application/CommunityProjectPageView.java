package com.socialcoisa.rede.communityprojects.application;

import java.util.List;

/** Página do catálogo de projetos; cursor é o deslocamento da próxima. */
public record CommunityProjectPageView(List<CommunityProjectView> items, String nextCursor) {
}
