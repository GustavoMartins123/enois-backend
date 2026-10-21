package com.socialcoisa.rede.securityaudit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.socialcoisa.rede.feed.application.port.in.ModerationRemovePostUseCase;
import com.socialcoisa.rede.feed.application.port.out.PostRepository;
import com.socialcoisa.rede.feed.domain.Post;
import com.socialcoisa.rede.feed.domain.PostId;
import com.socialcoisa.rede.feed.domain.PostType;
import com.socialcoisa.rede.feed.domain.UserId;
import com.socialcoisa.rede.identity.application.port.out.AuthSessionRepository;
import com.socialcoisa.rede.moderation.ai.ModerationAiService;
import com.socialcoisa.rede.moderation.application.ModeratorService;
import com.socialcoisa.rede.moderation.application.PostSpamAutomodService;
import com.socialcoisa.rede.moderation.application.SanctionService;
import com.socialcoisa.rede.shared.web.AdminGuard;

/** Test the production 2-minute throttle with a growing burst, not an already-complete burst. */
class SpamDetectionTimingAuditTests {
    @Test
    void aHarmlessFirstPostMustNotSilenceDetectionOfTheFollowingBurst() {
        Instant now = Instant.parse("2026-10-21T12:00:00Z");
        var posts = mock(PostRepository.class);
        var ai = mock(ModerationAiService.class);
        var service = new PostSpamAutomodService(posts, ai, mock(ModerationRemovePostUseCase.class),
            mock(SanctionService.class), mock(AuthSessionRepository.class), mock(AdminGuard.class),
            mock(ModeratorService.class), Clock.fixed(now, ZoneOffset.UTC),
            true, 3, 10, 8, 3, 8, 168, 50, 20, 2);
        var recent = new ArrayList<Post>();
        when(posts.findActiveByAuthor(any(), any(), anyInt())).thenAnswer(ignored -> List.copyOf(recent));
        recent.add(post("first", "primeiro post normal", now));
        assertThat(service.evaluate("audit_spam_timing").status()).isEqualTo("NO_SIGNAL");
        for (int i = 1; i < 8; i++) recent.add(post("burst_" + i, "publicacao distinta de flood " + i, now));
        // No second AI request is required: the first evaluation had no spam and never called AI.
        assertThat(service.evaluate("audit_spam_timing").status())
            .as("A primeira avaliacao sem sinal nao deve impedir reconhecer o limiar de flood")
            .isNotEqualTo("THROTTLED");
    }
    private Post post(String id, String content, Instant now) {
        return Post.publish(new PostId("audit_post_" + id), new UserId("audit_spam_timing"), PostType.INSIGHT,
            content, null, List.of(), List.of(), null, now.minusSeconds(1));
    }
}
