package com.socialcoisa.rede.jobs.adapters.in.web;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.socialcoisa.rede.idempotency.application.CommandResponse;
import com.socialcoisa.rede.idempotency.application.IdempotencyApplicationService;
import com.socialcoisa.rede.jobs.application.JobService;
import com.socialcoisa.rede.jobs.domain.Job;
import com.socialcoisa.rede.jobs.domain.JobApplicationStatus;
import com.socialcoisa.rede.jobs.domain.JobApplication;
import com.socialcoisa.rede.shared.web.ApiException;
import com.socialcoisa.rede.shared.web.CurrentUser;
import com.socialcoisa.rede.shared.web.CurrentUserResolver;
import com.socialcoisa.rede.shared.web.CursorCodec;
import com.socialcoisa.rede.shared.web.PageResponse;
import com.socialcoisa.rede.shared.web.StatusResponse;

@RestController
@RequestMapping("/jobs")
class JobContractController {

	private final IdempotencyApplicationService idempotency;
	private final CurrentUserResolver currentUserResolver;
	private final JobService jobService;
	private final CursorCodec cursorCodec;
	private final com.socialcoisa.rede.profile.application.port.out.UserProfileRepository userProfileRepository;
	private final com.socialcoisa.rede.profile.application.ReputationService reputationService;
	private final tools.jackson.databind.ObjectMapper objectMapper;

	JobContractController(IdempotencyApplicationService idempotency, CurrentUserResolver currentUserResolver,
			JobService jobService, CursorCodec cursorCodec,
			com.socialcoisa.rede.profile.application.port.out.UserProfileRepository userProfileRepository,
			com.socialcoisa.rede.profile.application.ReputationService reputationService,
			tools.jackson.databind.ObjectMapper objectMapper) {
		this.idempotency = idempotency;
		this.currentUserResolver = currentUserResolver;
		this.jobService = jobService;
		this.cursorCodec = cursorCodec;
		this.userProfileRepository = userProfileRepository;
		this.reputationService = reputationService;
		this.objectMapper = objectMapper;
	}

	/** Candidaturas recebidas (somente quem publicou a vaga). */
	@GetMapping("/{jobId}/applications")
	List<ApplicationView> applications(@PathVariable String jobId,
			@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestParam(defaultValue = "50") int limit) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		return jobService.applicationsOf(jobId, user.id().value(), limit).stream()
			.map(application -> new ApplicationView(application.id(), application.jobId(), application.status().name(),
				application.appliedAt().toString(), application.answers().size(),
				toCandidateView(application), application.message(),
				application.answers().stream()
					.map(answer -> new AnswerView(answer.label(), answer.value()))
					.toList()))
			.toList();
	}

	@PostMapping
	ResponseEntity<JobView> create(@RequestBody CreateJobRequest body,
			@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		Job job = jobService.create(body.title(), body.company(), body.logo(), body.location(), body.type(), body.level(),
			body.salary(), body.currency(), body.skills(), toFieldInputs(body.applicationFields()), user.id().value(),
			body.communityAgreement());
		return ResponseEntity.status(HttpStatus.CREATED).body(toView(job, user.id().value(), false, false, 0));
	}

	@GetMapping
	PageResponse<JobView> jobs(@RequestParam(required = false) String query,
			@RequestParam(required = false) String type, @RequestParam(required = false) String level,
			@RequestParam(required = false) String location,
			@RequestParam(required = false) List<String> skills,
			@RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") int limit,
			@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {
		Instant before = cursorCodec.decode(cursor).orElse(null);
		List<Job> jobs = jobService.list(query, type, level, location, skills, before, limit);
		return page(jobs, limit, viewerId(userId, sessionToken), PageKind.LIST);
	}

	/** Vagas publicadas pelo usuario autenticado (com o total de candidaturas recebidas). */
	@GetMapping("/mine")
	PageResponse<JobView> mine(@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "20") int limit,
			@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		Instant before = cursorCodec.decode(cursor).orElse(null);
		List<Job> jobs = jobService.listMine(user.id().value(), before, limit);
		return page(jobs, limit, user.id().value(), PageKind.MINE);
	}

	/** Vagas em que o usuario autenticado se candidatou. */
	@GetMapping("/applications")
	PageResponse<JobView> applications(@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "20") int limit,
			@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		Instant before = cursorCodec.decode(cursor).orElse(null);
		List<Job> jobs = jobService.listApplications(user.id().value(), before, limit);
		return page(jobs, limit, user.id().value(), PageKind.APPLICATIONS);
	}

	@GetMapping("/{jobId}")
	JobView job(@PathVariable String jobId,
			@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {
		String viewer = viewerId(userId, sessionToken);
		Job job = jobService.getById(jobId);
		boolean owner = viewer != null && viewer.equals(job.postedBy());
		boolean applied = viewer != null && jobService.appliedJobIds(viewer, List.of(job.id())).contains(job.id());
		boolean saved = viewer != null && jobService.savedJobIds(viewer, List.of(job.id())).contains(job.id());
		return toView(job, viewer, applied, saved, owner ? jobService.applicationsCount(job.id()) : 0);
	}

	@PutMapping("/{jobId}")
	ResponseEntity<JobView> update(@PathVariable String jobId, @RequestBody CreateJobRequest body,
			@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		Job job = jobService.update(jobId, body.title(), body.company(), body.logo(), body.location(), body.type(),
			body.level(), body.salary(), body.currency(), body.skills(), toFieldInputs(body.applicationFields()),
			user.id().value());
		return ResponseEntity.ok(toView(job, user.id().value(), false, false, jobService.applicationsCount(job.id())));
	}

	/**
	 * Triagem: o dono da vaga move a candidatura de estado.
	 *
	 * Usa `resolve` (que lanca) como o encerramento — autorizacao nao pode ser "nao resolvi a sessao,
	 * entao trato como se nao fosse o dono". O estado e validado no dominio, e o candidato e avisado
	 * depois de a mudanca estar persistida.
	 */
	@PatchMapping("/{jobId}/applications/{applicationId}")
	ResponseEntity<ApplicationView> updateApplicationStatus(@PathVariable String jobId,
			@PathVariable String applicationId,
			@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestBody UpdateApplicationStatusRequest body) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		JobApplicationStatus status = parseStatus(body == null ? null : body.status());
		JobApplication updated = jobService.updateApplicationStatus(jobId, applicationId, user.id().value(), status);
		return ResponseEntity.ok(new ApplicationView(updated.id(), updated.jobId(), updated.status().name(),
			updated.appliedAt().toString(), updated.answers().size()));
	}

	/** Estado desconhecido e erro do cliente (422), nao erro interno. */
	private JobApplicationStatus parseStatus(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new ApiException(422, "INVALID_STATUS", "Informe o novo estado da candidatura.");
		}
		try {
			return JobApplicationStatus.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
		}
		catch (IllegalArgumentException exception) {
			throw new ApiException(422, "INVALID_STATUS", "Estado de candidatura desconhecido: " + raw);
		}
	}

	/**
	 * Encerra a vaga. Usa `resolve` (que lanca) e nao o `viewerId` tolerante: autorizacao nao pode
	 * ser "nao resolvi a sessao, entao trato como se nao fosse o dono".
	 */
	@PostMapping("/{jobId}/close")
	ResponseEntity<JobView> close(@PathVariable String jobId,
			@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		Job job = jobService.close(jobId, user.id().value());
		return ResponseEntity.ok(
			toView(job, user.id().value(), false, false, jobService.applicationsCount(job.id())));
	}

	@DeleteMapping("/{jobId}")
	ResponseEntity<Void> delete(@PathVariable String jobId,
			@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		jobService.delete(jobId, user.id().value());
		return ResponseEntity.noContent().build();
	}

	/**
	 * Candidatura. O corpo traz a mensagem opcional e as respostas dos campos que o autor definiu —
	 * os obrigatorios sao validados aqui, e a candidatura e entregue ao empregador por e-mail e DM.
	 */
	@PostMapping("/{jobId}/applications")
	ResponseEntity<String> apply(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@PathVariable String jobId,
			@RequestBody(required = false) ApplyRequest body) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		String hash = idempotency.hash(Map.of("jobId", jobId, "body", body == null ? Map.of() : body));
		return idempotency.execute(user.id(), "POST", "/api/jobs/{jobId}/applications", idempotencyKey, hash,
			() -> {
				var application = jobService.apply(jobId, user.id().value(), body == null ? null : body.message(),
					toAnswerInputs(body == null ? null : body.answers()));
				return new CommandResponse(201, new ApplicationView(application.id(), jobId, application.status().name(),
					application.appliedAt().toString(), application.answers().size()));
			});
	}

	@PostMapping("/{jobId}/saves")
	ResponseEntity<String> save(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@PathVariable String jobId) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		String hash = idempotency.hash(Map.of("jobId", jobId));
		return idempotency.execute(user.id(), "POST", "/api/jobs/{jobId}/saves", idempotencyKey, hash,
			() -> {
				jobService.save(jobId, user.id().value());
				return new CommandResponse(200, new StatusResponse(jobId, "SAVED"));
			});
	}

	@DeleteMapping("/{jobId}/saves")
	ResponseEntity<String> removeSave(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@PathVariable String jobId) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		String hash = idempotency.hash(Map.of("jobId", jobId));
		return idempotency.execute(user.id(), "DELETE", "/api/jobs/{jobId}/saves", idempotencyKey, hash,
			() -> {
				jobService.removeSave(jobId, user.id().value());
				return new CommandResponse(200, new StatusResponse(jobId, "UNSAVED"));
			});
	}

	private enum PageKind {
		LIST, MINE, APPLICATIONS
	}

	private PageResponse<JobView> page(List<Job> jobs, int limit, String viewer, PageKind kind) {
		List<String> ids = jobs.stream().map(Job::id).toList();
		boolean authenticated = viewer != null;
		Set<String> appliedIds = switch (kind) {
			case APPLICATIONS -> Set.copyOf(ids);
			case MINE -> Set.of();
			case LIST -> authenticated ? jobService.appliedJobIds(viewer, ids) : Set.of();
		};
		Set<String> savedIds = authenticated ? jobService.savedJobIds(viewer, ids) : Set.of();
		Map<String, Integer> counts = authenticated ? jobService.applicationsCountByJobIds(ids) : Map.of();
		List<JobView> items = jobs.stream()
			.map(job -> {
				boolean owner = authenticated && viewer.equals(job.postedBy());
				return new JobView(job.id(), job.title(), job.company(), job.logo(), job.location(), job.type(),
					job.level(), job.salary(), job.currency(), job.skills(),
					job.postedAt() != null ? job.postedAt().toString() : null, 0,
					appliedIds.contains(job.id()), savedIds.contains(job.id()), owner,
					owner ? counts.getOrDefault(job.id(), 0) : 0, authorOf(job), fieldViews(job),
					job.status().name(), job.closedAt() != null ? job.closedAt().toString() : null);
			})
			.toList();
		String nextCursor = jobs.size() == limit
			? cursorCodec.encode(jobs.get(jobs.size() - 1).postedAt()) : null;
		return new PageResponse<>(items, nextCursor);
	}

	private JobView toView(Job job, String viewer, boolean applied, boolean saved, int applicationsCount) {
		return new JobView(job.id(), job.title(), job.company(), job.logo(), job.location(), job.type(), job.level(),
			job.salary(), job.currency(), job.skills(), job.postedAt() != null ? job.postedAt().toString() : null, 0,
			applied, saved, viewer != null && viewer.equals(job.postedBy()), applicationsCount, authorOf(job),
			fieldViews(job), job.status().name(), job.closedAt() != null ? job.closedAt().toString() : null);
	}

	/** Quem criou a vaga (com reputacao e verificacao): o candidato precisa saber com quem fala. */
	private AuthorView authorOf(Job job) {
		var profile = userProfileRepository.findById(job.postedBy()).orElse(null);
		if (profile == null) {
			return null;
		}
		return new AuthorView(profile.id(), profile.username(), profile.name(), profile.avatar(), profile.headline(),
			reputationService.reputationOf(profile.id()), profile.identityVerified());
	}

	private List<ApplicationFieldView> fieldViews(Job job) {
		return job.applicationFields().stream()
			.map(field -> new ApplicationFieldView(field.id(), field.label(), field.type().name(), field.required()))
			.toList();
	}

	/** Resolve o usuario quando ha sessao; listas publicas seguem funcionando sem login. */
	private String viewerId(String userId, String sessionToken) {
		try {
			return currentUserResolver.resolve(userId, sessionToken).id().value();
		}
		catch (RuntimeException exception) {
			return null;
		}
	}

	record JobView(String id, String title, String company, String logo, String location, String type, String level,
			String salary, String currency, List<String> skills, String postedAt, int matchScore, boolean applied,
			boolean saved, boolean owner, int applicationsCount, AuthorView author, List<ApplicationFieldView>
			applicationFields, String status, String closedAt) {
	}

	/** Quem publicou a vaga, com o que sustenta a credibilidade dele. */
	record AuthorView(String id, String username, String name, String avatar, String headline, int reputation,
			boolean identityVerified) {
	}

	record ApplicationFieldView(String id, String label, String type, boolean required) {
	}

	record CreateJobRequest(String title, String company, String logo, String location, String type, String level,
			String salary, String currency, List<String> skills, boolean communityAgreement,
			List<ApplicationFieldRequest> applicationFields) {
	}

	record ApplicationFieldRequest(String label, String type, boolean required) {
	}

	record ApplyRequest(String message, List<AnswerRequest> answers) {
	}

	record UpdateApplicationStatusRequest(String status) {
	}

	record AnswerRequest(String fieldId, String value) {
	}

	/** Candidatura como o empregador ve: o retrato do candidato + as respostas. */
	record ApplicationView(String id, String jobId, String status, String appliedAt, int answersCount,
			CandidateView candidate, String message, List<AnswerView> answers) {

		ApplicationView(String id, String jobId, String status, String appliedAt, int answersCount) {
			this(id, jobId, status, appliedAt, answersCount, null, null, List.of());
		}
	}

	record CandidateView(String userId, String name, String username, String headline, String location, String avatar,
			List<String> skills, int experienceYears, int experiencesCount, int reputation, boolean identityVerified) {
	}

	record AnswerView(String label, String value) {
	}

	private List<JobService.JobFieldInput> toFieldInputs(List<ApplicationFieldRequest> fields) {
		if (fields == null) {
			return List.of();
		}
		return fields.stream()
			.map(field -> new JobService.JobFieldInput(field.label(), field.type(), field.required()))
			.toList();
	}

	private List<JobService.JobAnswerInput> toAnswerInputs(List<AnswerRequest> answers) {
		if (answers == null) {
			return List.of();
		}
		return answers.stream()
			.map(answer -> new JobService.JobAnswerInput(answer.fieldId(), answer.value()))
			.toList();
	}

	/** Le o retrato do candidato congelado na candidatura (JSON gravado no envio). */
	private CandidateView toCandidateView(com.socialcoisa.rede.jobs.domain.JobApplication application) {
		try {
			var snapshot = objectMapper.readTree(application.candidateSnapshot() == null ? "{}" : application.candidateSnapshot());
			return new CandidateView(
				application.userId(),
				snapshot.path("name").asText(""),
				snapshot.path("username").asText(""),
				snapshot.path("headline").asText(""),
				snapshot.path("location").asText(""),
				snapshot.path("avatar").asText(""),
				java.util.stream.StreamSupport.stream(snapshot.path("skills").spliterator(), false)
					.map(node -> node.asText("")).filter(text -> !text.isBlank()).toList(),
				snapshot.path("experienceYears").asInt(0),
				snapshot.path("experiencesCount").asInt(0),
				snapshot.path("reputation").asInt(0),
				snapshot.path("identityVerified").asBoolean(false));
		}
		catch (Exception exception) {
			return new CandidateView(application.userId(), "", "", "", "", "", List.of(), 0, 0, 0, false);
		}
	}

}
