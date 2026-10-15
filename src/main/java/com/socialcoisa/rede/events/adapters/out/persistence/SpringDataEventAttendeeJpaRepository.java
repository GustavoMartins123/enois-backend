package com.socialcoisa.rede.events.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataEventAttendeeJpaRepository extends JpaRepository<EventAttendeeJpaEntity, Long> {

	boolean existsByEventIdAndUserId(String eventId, String userId);

	void deleteByEventIdAndUserId(String eventId, String userId);

	/** Apaga todas as presencas de um evento (sanitizacao: some o vinculo com cada pessoa). */
	void deleteByEventId(String eventId);

	int countByEventId(String eventId);

}
