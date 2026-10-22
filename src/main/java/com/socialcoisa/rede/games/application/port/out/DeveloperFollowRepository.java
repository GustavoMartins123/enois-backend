package com.socialcoisa.rede.games.application.port.out;

import java.util.List;

public interface DeveloperFollowRepository {

	boolean exists(String followerId, String developerUserId);

	void follow(String id, String followerId, String developerUserId, java.time.Instant now);

	int unfollow(String followerId, String developerUserId);

	long countByDeveloper(String developerUserId);

	/** Ids dos criadores que esta pessoa acompanha. */
	List<String> followedBy(String followerId);

	/** Ids de quem acompanha este criador (para avisar quando ele publicar). */
	List<String> followersOf(String developerUserId);
}
