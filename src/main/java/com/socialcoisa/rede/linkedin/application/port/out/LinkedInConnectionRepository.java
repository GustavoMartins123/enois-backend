package com.socialcoisa.rede.linkedin.application.port.out;

import java.util.Optional;

import com.socialcoisa.rede.linkedin.domain.LinkedInConnection;

public interface LinkedInConnectionRepository {

	Optional<LinkedInConnection> findByUserId(String userId);

	void save(LinkedInConnection connection);

	void deleteByUserId(String userId);
}
