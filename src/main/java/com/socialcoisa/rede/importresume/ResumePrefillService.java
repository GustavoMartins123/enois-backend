package com.socialcoisa.rede.importresume;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.socialcoisa.rede.jobs.application.port.out.JobRepository;
import com.socialcoisa.rede.jobs.domain.Job;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Prefill da inscricao a partir do curriculo: le a vaga (titulo + campos do formulario),
 * extrai os contatos do texto do curriculo e devolve mensagem + respostas sugeridas.
 *
 * Somente leitura na vaga (via porta `JobRepository`, sem tocar no `JobService`): nao grava
 * candidatura nem altera nada — a inscricao continua no POST existente, com a pessoa
 * revisando as sugestoes antes de enviar. Vaga externa nao tem formulario interno (a
 * candidatura e no link de origem), entao o prefill e recusado para elas.
 */
@Service
public class ResumePrefillService {

	private final JobRepository jobs;
	private final ResumeImportService resumes;

	public ResumePrefillService(JobRepository jobs, ResumeImportService resumes) {
		this.jobs = jobs;
		this.resumes = resumes;
	}

	@Transactional(readOnly = true)
	public ResumeImportService.ApplyPrefillView prefill(String userId, String jobId, String resumeText) {
		Job job = jobs.findById(jobId)
			.orElseThrow(() -> new ApiException(404, "JOB_NOT_FOUND", "Vaga nao encontrada."));
		if (!job.isOpen()) {
			throw new ApiException(409, "JOB_CLOSED", "Esta vaga foi encerrada e nao recebe mais candidaturas.");
		}
		if (job.postedBy().equals(userId)) {
			throw new ApiException(400, "CANNOT_APPLY_TO_OWN_JOB", "Voce nao pode se candidatar a sua propria vaga.");
		}
		List<ResumeImportService.JobField> fields = job.applicationFields().stream()
			.map(field -> new ResumeImportService.JobField(field.id(), field.label(),
				ResumeImportService.JobFieldType.valueOf(field.type().name()), field.required()))
			.toList();
		return resumes.prefill(jobId, job.title(), fields, resumeText);
	}
}
