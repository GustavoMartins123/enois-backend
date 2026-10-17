package com.socialcoisa.rede.blog.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataBlogSubscriptionJpaRepository extends JpaRepository<BlogSubscriptionJpaEntity, String> {

	Optional<BlogSubscriptionJpaEntity> findByBlogIdAndUserId(String blogId, String userId);

	@Modifying
	@Query("DELETE FROM BlogSubscriptionJpaEntity s WHERE s.blogId = :blogId AND s.userId = :userId")
	int deleteByBlogAndUser(@Param("blogId") String blogId, @Param("userId") String userId);

	@Query("SELECT s.userId FROM BlogSubscriptionJpaEntity s WHERE s.blogId = :blogId")
	List<String> findSubscriberIds(@Param("blogId") String blogId);

	@Query("SELECT s.blogId FROM BlogSubscriptionJpaEntity s WHERE s.userId = :userId")
	List<String> findSubscribedBlogIds(@Param("userId") String userId);
}
