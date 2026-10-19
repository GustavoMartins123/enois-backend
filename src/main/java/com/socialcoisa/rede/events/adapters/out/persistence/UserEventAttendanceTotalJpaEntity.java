package com.socialcoisa.rede.events.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Presenças em eventos como contribuição PERMANENTE: o sanitizador apaga as linhas de
 * `event_attendees` (PII), e este agregado garante que a credibilidade de quem participou não
 * caia sozinha com o tempo. Só a contagem — nenhum dado pessoal do evento.
 */
@Entity
@Table(name = "user_event_attendance_totals")
class UserEventAttendanceTotalJpaEntity {

	@Id
	@Column(name = "user_id")
	private String userId;

	@Column(nullable = false)
	private int total;

	protected UserEventAttendanceTotalJpaEntity() {
	}

	UserEventAttendanceTotalJpaEntity(String userId, int total) {
		this.userId = userId;
		this.total = total;
	}

	String getUserId() {
		return userId;
	}

	int getTotal() {
		return total;
	}

	void increment() {
		this.total++;
	}
}
