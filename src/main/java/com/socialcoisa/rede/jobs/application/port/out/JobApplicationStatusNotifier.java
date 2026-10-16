package com.socialcoisa.rede.jobs.application.port.out;

import com.socialcoisa.rede.jobs.domain.Job;
import com.socialcoisa.rede.jobs.domain.JobApplication;
import com.socialcoisa.rede.jobs.domain.JobApplicationStatus;

/**
 * Avisa o CANDIDATO que o estado da candidatura dele mudou.
 *
 * E o inverso do {@link JobApplicationDelivery} (que avisa o empregador sobre candidatura nova):
 * destinatarios e eventos diferentes, por isso uma porta propria em vez de sobrecarregar a outra.
 */
public interface JobApplicationStatusNotifier {

	void notifyStatusChange(Job job, JobApplication application, JobApplicationStatus status);
}
