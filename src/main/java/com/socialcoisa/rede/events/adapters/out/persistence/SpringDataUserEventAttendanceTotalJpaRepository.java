package com.socialcoisa.rede.events.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataUserEventAttendanceTotalJpaRepository
		extends JpaRepository<UserEventAttendanceTotalJpaEntity, String> {
}
