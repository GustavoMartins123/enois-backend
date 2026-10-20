package com.socialcoisa.rede.moderation.application.port.out;

import java.time.Instant;
import java.util.List;

public interface BanContentArchiveRepository {

	void archive(String sanctionId, String userId, String contentType, String contentId,
			String contentSnapshot, Instant archivedAt);

	List<BanArchiveEntry> findBySanction(String sanctionId);

	record BanArchiveEntry(String id, String sanctionId, String userId, String contentType,
			String contentId, String contentSnapshot, Instant archivedAt) {
	}
}
