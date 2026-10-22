package com.socialcoisa.rede.moderation.adapters.out.persistence;

import java.util.List;

import org.springframework.stereotype.Component;

import com.socialcoisa.rede.shared.security.IpBlockReader;

/** Implementa a porta compartilhada de bloqueio de IP com o estado real da lista. */
@Component
class IpBlockAdapter implements IpBlockReader {

	private final SpringDataBlockedIpJpaRepository repository;

	IpBlockAdapter(SpringDataBlockedIpJpaRepository repository) {
		this.repository = repository;
	}

	@Override
	public boolean blocked(String ip) {
		if (ip == null || ip.isBlank()) {
			return false;
		}
		return repository.existsByIp(ip.trim());
	}
}
