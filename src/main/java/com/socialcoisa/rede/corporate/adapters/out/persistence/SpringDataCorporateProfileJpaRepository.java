package com.socialcoisa.rede.corporate.adapters.out.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataCorporateProfileJpaRepository
		extends JpaRepository<CorporateProfileJpaEntity, String> {

	Optional<CorporateProfileJpaEntity> findByUsername(String username);

	boolean existsByUsername(String username);
}
