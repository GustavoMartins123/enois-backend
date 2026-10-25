package com.socialcoisa.rede.ops.domain;

import java.time.Instant;

/**
 * Metadado de um e-mail enviado pela plataforma. O corpo não é gravado: o painel mostra para quem,
 * assunto, tipo e se o envio funcionou. Serve para o time enxergar falhas de SMTP e o volume de
 * avisos sem transformar o painel num arquivo de mensagens pessoais.
 */
public record EmailLog(String id, String toEmail, String subject, String kind, EmailLogStatus status,
		String error, Instant createdAt, String clientIp) {

	public static EmailLog create(String id, String toEmail, String subject, String kind, EmailLogStatus status,
			String error, Instant now, String clientIp) {
		return new EmailLog(id, toEmail, subject, kind, status, error, now, clientIp);
	}
}
