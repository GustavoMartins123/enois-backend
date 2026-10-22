package com.socialcoisa.rede.themes.adapters.out.persistence;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.socialcoisa.rede.themes.domain.ThemeGalleryStatus;

interface SpringDataThemeGalleryJpaRepository extends JpaRepository<ThemePackageJpaEntity, String> {

	List<ThemePackageJpaEntity> findByStatusOrderByCreatedAtDesc(ThemeGalleryStatus status, Pageable pageable);

	List<ThemePackageJpaEntity> findTop50ByAuthorIdOrderByCreatedAtDesc(String authorId);
}
