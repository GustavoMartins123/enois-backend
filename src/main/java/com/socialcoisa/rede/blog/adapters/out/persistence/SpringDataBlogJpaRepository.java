package com.socialcoisa.rede.blog.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataBlogJpaRepository extends JpaRepository<BlogJpaEntity, String> {

	Optional<BlogJpaEntity> findByUserId(String userId);

	Optional<BlogJpaEntity> findBySlug(String slug);

	List<BlogJpaEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
