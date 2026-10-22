package com.socialcoisa.rede.support.adapters.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Valor cifrado de uma configuração do suporte (token do GitHub, repo, segredo do webhook). */
@Entity
@Table(name = "support_settings")
class SupportSettingJpaEntity {

	@Id
	@Column(length = 64)
	private String name;

	@Column(name = "value_cipher", nullable = false, length = 4096)
	private String valueCipher;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected SupportSettingJpaEntity() {
	}

	SupportSettingJpaEntity(String name, String valueCipher, Instant updatedAt) {
		this.name = name;
		this.valueCipher = valueCipher;
		this.updatedAt = updatedAt;
	}

	String name() {
		return name;
	}

	String valueCipher() {
		return valueCipher;
	}

	void update(String valueCipher, Instant updatedAt) {
		this.valueCipher = valueCipher;
		this.updatedAt = updatedAt;
	}
}
