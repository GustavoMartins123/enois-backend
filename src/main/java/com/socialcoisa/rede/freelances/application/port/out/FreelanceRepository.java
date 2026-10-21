package com.socialcoisa.rede.freelances.application.port.out;

import java.util.List;
import java.util.Optional;

import com.socialcoisa.rede.freelances.domain.Freelance;

public interface FreelanceRepository {
	Freelance save(Freelance freelance);
	Optional<Freelance> findById(String id);
	List<Freelance> findOpen(int limit, int offset);
	List<Freelance> findByUser(String userId);
	long countOpen();
}
