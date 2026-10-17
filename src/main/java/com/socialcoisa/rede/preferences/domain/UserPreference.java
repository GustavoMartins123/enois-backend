package com.socialcoisa.rede.preferences.domain;

import java.time.Instant;

/** Preferência que acompanha a conta; hoje, o tema da coleção ativa. */
public record UserPreference(String userId, String themeId, Instant updatedAt) {
}
