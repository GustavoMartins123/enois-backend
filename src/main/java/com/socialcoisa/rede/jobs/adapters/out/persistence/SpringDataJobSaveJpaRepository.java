package com.socialcoisa.rede.jobs.adapters.out.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataJobSaveJpaRepository extends JpaRepository<JobSaveJpaEntity, Long> {

	boolean existsByJobIdAndUserId(String jobId, String userId);

	void deleteByJobIdAndUserId(String jobId, String userId);

	void deleteByJobId(String jobId);

	List<JobSaveJpaEntity> findByUserIdOrderBySavedAtDesc(String userId);

	@Query("SELECT s.jobId FROM JobSaveJpaEntity s WHERE s.userId = :userId AND s.jobId IN :jobIds")
	List<String> findJobIdsByUserIdAndJobIdIn(@Param("userId") String userId,
			@Param("jobIds") java.util.Collection<String> jobIds);

}
