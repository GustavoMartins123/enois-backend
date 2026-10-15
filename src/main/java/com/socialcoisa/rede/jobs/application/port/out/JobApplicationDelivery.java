package com.socialcoisa.rede.jobs.application.port.out;

import com.socialcoisa.rede.jobs.domain.Job;
import com.socialcoisa.rede.jobs.domain.JobApplication;

/**
 * Entrega da candidatura para quem publicou a vaga.
 *
 * E uma porta (e nao uma chamada direta) porque a entrega tem dois canais — e-mail e mensagem
 * direta — e falha neles nao pode derrubar a candidatura, que ja esta registrada.
 */
public interface JobApplicationDelivery {

	void deliver(Job job, JobApplication application);
}
