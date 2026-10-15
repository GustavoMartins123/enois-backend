package com.socialcoisa.rede.support.application.port.out;

import java.util.List;

import com.socialcoisa.rede.support.domain.SupportRequest;
import com.socialcoisa.rede.support.domain.SupportStatus;

public interface SupportRequestRepository {

	SupportRequest save(SupportRequest request);

	List<SupportRequest> findByStatus(SupportStatus status, int limit);

	long countByUserSince(String userId, java.time.Instant since);
}
