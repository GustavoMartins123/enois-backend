package com.socialcoisa.rede.preferences.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataUserPreferenceJpaRepository extends JpaRepository<UserPreferenceJpaEntity, String> {
}
