package com.socialcoisa.rede.moderation.application.port.out;

import java.util.List;
import java.util.Optional;

import com.socialcoisa.rede.moderation.domain.Appeal;
import com.socialcoisa.rede.moderation.domain.AppealStatus;

public interface AppealRepository {

	Appeal save(Appeal appeal);

	Optional<Appeal> findById(String id);

	List<Appeal> findByStatus(AppealStatus status, int limit);

	List<Appeal> findByUser(String userId, int limit);

	boolean existsPendingByUser(String userId);
}
