package com.socialcoisa.rede.jobs.domain;

/** Estado do processo seletivo da vaga. */
public enum JobStatus {

	/** Aceitando candidaturas. */
	OPEN,

	/** Encerrada pelo autor: nao aceita mais candidatura, mas preserva o que ja chegou. */
	CLOSED
}
