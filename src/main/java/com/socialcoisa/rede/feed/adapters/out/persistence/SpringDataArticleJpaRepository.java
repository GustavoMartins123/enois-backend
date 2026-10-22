package com.socialcoisa.rede.feed.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataArticleJpaRepository extends JpaRepository<ArticleJpaEntity, String> {
}
