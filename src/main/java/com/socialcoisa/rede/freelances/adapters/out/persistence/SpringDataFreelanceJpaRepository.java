package com.socialcoisa.rede.freelances.adapters.out.persistence;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataFreelanceJpaRepository extends JpaRepository<FreelanceJpaEntity, String> {
	List<FreelanceJpaEntity> findByStatusOrderByCreatedAtDesc(com.socialcoisa.rede.freelances.domain.Freelance.Status status, Pageable page);
	List<FreelanceJpaEntity> findByUserIdOrderByCreatedAtDesc(String userId);
	long countByStatus(com.socialcoisa.rede.freelances.domain.Freelance.Status status);

	@Query("""
		select f from FreelanceJpaEntity f
		where f.status = :status
		  and (lower(f.title) like lower(concat('%', :q, '%'))
		    or lower(f.scope) like lower(concat('%', :q, '%')))
		order by f.createdAt desc
		""")
	List<FreelanceJpaEntity> searchOpen(@Param("status") com.socialcoisa.rede.freelances.domain.Freelance.Status status,
			@Param("q") String q, Pageable page);
}
