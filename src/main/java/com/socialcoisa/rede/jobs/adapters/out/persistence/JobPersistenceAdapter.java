package com.socialcoisa.rede.jobs.adapters.out.persistence;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.jobs.application.port.out.JobRepository;
import com.socialcoisa.rede.jobs.domain.Job;

@Repository
class JobPersistenceAdapter implements JobRepository {

	private final SpringDataJobJpaRepository springData;
	private final SpringDataJobApplicationJpaRepository applicationRepo;
	private final SpringDataJobSaveJpaRepository saveRepo;
	private final Clock clock;

	JobPersistenceAdapter(SpringDataJobJpaRepository springData,
			SpringDataJobApplicationJpaRepository applicationRepo,
			SpringDataJobSaveJpaRepository saveRepo, Clock clock) {
		this.springData = springData;
		this.applicationRepo = applicationRepo;
		this.saveRepo = saveRepo;
		this.clock = clock;
	}

	@Override
	public void save(Job job) {
		springData.save(JobJpaEntity.fromDomain(job));
	}

	@Override
	public Optional<Job> findById(String id) {
		return springData.findById(id).map(JobJpaEntity::toDomain);
	}

	@Override
	public List<Job> findAll(String query, String type, String level, String location, List<String> skills,
			Instant before, int limit) {
		PageRequest page = PageRequest.of(0, limit);
		List<JobJpaEntity> entities;
		if (StringUtils.hasText(query)) {
			entities = springData.searchByQuery(query, before, page);
		} else if (skills != null && !skills.isEmpty()) {
			entities = springData.findBySkills(skills, before, page);
		} else if (StringUtils.hasText(level)) {
			entities = springData.findByLevel(level, before, page);
		} else if (StringUtils.hasText(type)) {
			entities = springData.findByType(type, before, page);
		} else if (StringUtils.hasText(location)) {
			entities = springData.findByLocation(location, before, page);
		} else {
			entities = springData.findAllBefore(before, page);
		}
		return entities.stream().map(JobJpaEntity::toDomain).toList();
	}

	@Override
	public void saveApplication(String jobId, String userId) {
		applicationRepo.save(JobApplicationJpaEntity.create(jobId, userId, clock.instant()));
	}

	@Override
	public void removeApplication(String jobId, String userId) {
		applicationRepo.deleteByJobIdAndUserId(jobId, userId);
	}

	@Override
	public boolean hasApplied(String jobId, String userId) {
		return applicationRepo.existsByJobIdAndUserId(jobId, userId);
	}

	@Override
	public void saveBookmark(String jobId, String userId) {
		saveRepo.save(JobSaveJpaEntity.create(jobId, userId, clock.instant()));
	}

	@Override
	public void removeBookmark(String jobId, String userId) {
		saveRepo.deleteByJobIdAndUserId(jobId, userId);
	}

	@Override
	public boolean hasBookmarked(String jobId, String userId) {
		return saveRepo.existsByJobIdAndUserId(jobId, userId);
	}

	@Override
	public void deleteById(String id) {
		applicationRepo.deleteByJobId(id);
		saveRepo.deleteByJobId(id);
		springData.deleteById(id);
	}

	@Override
	public List<Job> findByPostedBy(String userId, Instant before, int limit) {
		return springData.findByPostedByAndPostedAtBeforeOrderByPostedAtDesc(userId, before, PageRequest.of(0, limit))
			.stream().map(JobJpaEntity::toDomain).toList();
	}

	@Override
	public List<Job> findAppliedByUser(String userId, Instant before, int limit) {
		List<String> jobIds = applicationRepo.findJobIdsByUserId(userId, PageRequest.of(0, limit));
		if (jobIds.isEmpty()) {
			return List.of();
		}
		java.util.Map<String, Job> byId = springData.findByIdIn(jobIds).stream()
			.map(JobJpaEntity::toDomain)
			.collect(java.util.stream.Collectors.toMap(Job::id, job -> job));
		// Mantem a ordem das candidaturas (mais recentes primeiro), aplicando o cursor.
		return jobIds.stream()
			.map(byId::get)
			.filter(java.util.Objects::nonNull)
			.filter(job -> before == null || !job.postedAt().isAfter(before))
			.toList();
	}

	@Override
	public int countApplications(String jobId) {
		return applicationRepo.countByJobId(jobId);
	}

	@Override
	public java.util.Set<String> findAppliedJobIds(String userId, java.util.Collection<String> jobIds) {
		if (jobIds == null || jobIds.isEmpty()) {
			return java.util.Set.of();
		}
		return java.util.Set.copyOf(applicationRepo.findJobIdsByUserIdAndJobIdIn(userId, jobIds));
	}

	@Override
	public java.util.Set<String> findSavedJobIds(String userId, java.util.Collection<String> jobIds) {
		if (jobIds == null || jobIds.isEmpty()) {
			return java.util.Set.of();
		}
		return java.util.Set.copyOf(saveRepo.findJobIdsByUserIdAndJobIdIn(userId, jobIds));
	}

	@Override
	public java.util.Map<String, Integer> countApplicationsByJobIds(java.util.Collection<String> jobIds) {
		if (jobIds == null || jobIds.isEmpty()) {
			return java.util.Map.of();
		}
		return applicationRepo.countByJobIdGrouped(jobIds).stream()
			.collect(java.util.stream.Collectors.toMap(row -> (String) row[0], row -> ((Number) row[1]).intValue()));
	}

}
