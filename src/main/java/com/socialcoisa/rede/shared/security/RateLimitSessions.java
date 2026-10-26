package com.socialcoisa.rede.shared.security;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.socialcoisa.rede.identity.application.port.out.AuthSessionRepository;

/**
 * Which user a session token belongs to, for keying rate-limit buckets.
 *
 * <p>Only a live session earns its own bucket; an invented token is counted against its IP. Live
 * sessions are cached briefly so the limiters do not hit the database on every request, and each IP
 * gets a small budget of lookups for tokens that are not cached: a real session costs one lookup, while
 * a stream of invented tokens runs out of budget and stops reaching the database. A session revoked
 * inside the cache window keeps its bucket for that window; authentication itself is still checked per
 * request by the resolver.
 *
 * <p>The repository is resolved on first use: filters are built before the JPA context is complete.
 */
@Component
public class RateLimitSessions {

	private static final long TTL_MS = 60_000L;
	private static final int MAX_ENTRIES = 100_000;
	private static final long LOOKUP_WINDOW_MS = 60_000L;
	private static final int LOOKUPS_PER_WINDOW = 60;

	private final ObjectProvider<AuthSessionRepository> sessions;
	private final Clock clock;
	private final Map<String, Cached> known = new ConcurrentHashMap<>();
	private final Map<String, Window> lookups = new ConcurrentHashMap<>();

	public RateLimitSessions(ObjectProvider<AuthSessionRepository> sessions, Clock clock) {
		this.sessions = sessions;
		this.clock = clock;
	}

	/** User id of a live session, or null; {@code ip} is the caller's resolved address. */
	public String userId(String token, String ip) {
		String trimmed = token.trim();
		String hash = Hashes.sha256Hex(trimmed);
		long now = clock.millis();
		Cached cached = known.get(hash);
		if (cached != null && cached.until() > now) {
			return cached.userId();
		}
		if (!takeLookup(ip, now)) {
			return null;
		}
		String userId = sessions.getObject().findActiveUserId(trimmed, clock.instant()).orElse(null);
		if (userId != null) {
			if (known.size() > MAX_ENTRIES) {
				// ponytail: drop-all on overflow, an LRU only if the limiter cache ever shows up in profiles
				known.clear();
			}
			known.put(hash, new Cached(userId, now + TTL_MS));
		}
		return userId;
	}

	private boolean takeLookup(String ip, long now) {
		if (lookups.size() > MAX_ENTRIES) {
			lookups.clear();
		}
		Window window = lookups.compute(ip, (key, current) -> current == null || now - current.start() >= LOOKUP_WINDOW_MS
			? new Window(now, 1)
			: new Window(current.start(), current.count() + 1));
		return window.count() <= LOOKUPS_PER_WINDOW;
	}

	private record Cached(String userId, long until) {
	}

	private record Window(long start, int count) {
	}
}
