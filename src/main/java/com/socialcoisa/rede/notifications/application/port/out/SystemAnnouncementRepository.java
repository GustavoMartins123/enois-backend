package com.socialcoisa.rede.notifications.application.port.out;

import java.time.Instant;

/** Marca de comunicado de sistema já enviado (idempotência de envio em massa). */
public interface SystemAnnouncementRepository {

	boolean isSent(String key);

	void markSent(String key, Instant at);
}
