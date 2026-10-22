package com.socialcoisa.rede.identity.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataKnownDeviceJpaRepository extends JpaRepository<KnownDeviceJpaEntity, String> {

	boolean existsByUserIdAndFingerprint(String userId, String fingerprint);
}
