package com.socialcoisa.rede.securityaudit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.socialcoisa.rede.feed.domain.UserId;
import com.socialcoisa.rede.profile.application.port.out.UserProfileRepository;
import com.socialcoisa.rede.profile.domain.UserProfile;
import com.socialcoisa.rede.shared.web.AdminGuard;
import com.socialcoisa.rede.shared.web.CurrentUser;

class AdminAuthorityAuditTests {
    @Test
    void immutableIdKeepsAuthorityAfterAProfileRename() {
        var profiles = mock(UserProfileRepository.class);
        var profile = mock(UserProfile.class);
        when(profile.id()).thenReturn("fixed-admin-id");
        when(profile.username()).thenReturn("new-display-name");
        when(profiles.findById("fixed-admin-id")).thenReturn(Optional.of(profile));
        assertThat(new AdminGuard(profiles, "", "fixed-admin-id")
            .isAdmin(new CurrentUser(new UserId("fixed-admin-id")))).isTrue();
    }

    @Test
    void anUnverifiedAllowlistedEmailCannotGrantAuthority() {
        var profiles = mock(UserProfileRepository.class);
        var profile = mock(UserProfile.class);
        when(profile.id()).thenReturn("ordinary-id");
        when(profile.email()).thenReturn("reserved@example.invalid");
        when(profile.emailVerified()).thenReturn(false);
        when(profiles.findById("ordinary-id")).thenReturn(Optional.of(profile));
        assertThat(new AdminGuard(profiles, "reserved@example.invalid", "")
            .isAdmin(new CurrentUser(new UserId("ordinary-id")))).isFalse();
    }

    @Test
    void verifiedEmailAllowlistRemainsSupported() {
        var profiles = mock(UserProfileRepository.class);
        var profile = mock(UserProfile.class);
        when(profile.id()).thenReturn("verified-admin-id");
        when(profile.email()).thenReturn("Team@Example.invalid");
        when(profile.emailVerified()).thenReturn(true);
        when(profiles.findById("verified-admin-id")).thenReturn(Optional.of(profile));
        assertThat(new AdminGuard(profiles, "team@example.invalid", "")
            .isAdmin(new CurrentUser(new UserId("verified-admin-id")))).isTrue();
    }
}
