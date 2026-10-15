package com.socialcoisa.rede.jobs.application.port.out;

import java.util.List;
import java.util.Optional;

import com.socialcoisa.rede.jobs.domain.Job;

public interface JobRepository {

	void save(Job job);

	Optional<Job> findById(String id);

	List<Job> findAll(String query, String type, String level, String location, List<String> skills,
			java.time.Instant before, int limit);

	void deleteById(String id);

	/** Vagas publicadas pelo usuario. */
	List<Job> findByPostedBy(String userId, java.time.Instant before, int limit);

	/** Vagas em que o usuario se candidatou, mais recentes primeiro. */
	List<Job> findAppliedByUser(String userId, java.time.Instant before, int limit);

	int countApplications(String jobId);

	/** Para listas: ids em que o usuario se candidatou/salvou, em consultas em lote. */
	java.util.Set<String> findAppliedJobIds(String userId, java.util.Collection<String> jobIds);

	java.util.Set<String> findSavedJobIds(String userId, java.util.Collection<String> jobIds);

	java.util.Map<String, Integer> countApplicationsByJobIds(java.util.Collection<String> jobIds);

	void saveApplication(String jobId, String userId);

	void removeApplication(String jobId, String userId);

	boolean hasApplied(String jobId, String userId);

	void saveBookmark(String jobId, String userId);

	void removeBookmark(String jobId, String userId);

	boolean hasBookmarked(String jobId, String userId);

}
