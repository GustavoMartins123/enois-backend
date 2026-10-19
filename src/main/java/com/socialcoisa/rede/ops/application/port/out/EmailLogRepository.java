package com.socialcoisa.rede.ops.application.port.out;

import java.util.List;

import com.socialcoisa.rede.ops.domain.EmailLog;

public interface EmailLogRepository {

	EmailLog save(EmailLog log);

	/** Mais recentes primeiro; o painel mostra apenas metadados. */
	List<EmailLog> findRecent(int limit);
}
