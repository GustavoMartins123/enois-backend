package com.socialcoisa.rede.identity.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    private static final Logger LOG = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final String frontendUrl;
    private final String fromEmail;

    public EmailService(
            JavaMailSender mailSender,
            TemplateEngine templateEngine,
            @Value("${rede.frontend.url}") String frontendUrl,
            @Value("${spring.mail.username}") String fromEmail) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.frontendUrl = frontendUrl.endsWith("/")
                ? frontendUrl.substring(0, frontendUrl.length() - 1)
                : frontendUrl;
        this.fromEmail = fromEmail;
    }

    public void sendPasswordResetEmail(String toEmail, String userName, String resetToken) {
        String resetLink = buildResetLink(resetToken);
        String subject = "Redefinição de senha";
        String htmlBody = renderTemplate(userName, resetLink);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
        } catch (Exception e) {
            LOG.error("Falha ao enviar email de reset para {}: {}", toEmail, e.getMessage(), e);
        }
    }

    private String buildResetLink(String resetToken) {
        return frontendUrl + "/auth/reset?token=" + resetToken;
    }

    private String renderTemplate(String userName, String resetLink) {
        Context context = new Context();
        context.setVariable("userName", userName);
        context.setVariable("resetLink", resetLink);
        context.setVariable("currentYear", java.time.Year.now().getValue());
        return templateEngine.process("password-reset", context);
    }

	/** Notifica o time de suporte sobre um pedido aberto no app (responde para o usuario). */
	public void sendSupportRequestEmail(String toEmail, String requestSubject, String requestMessage, String replyTo) {
		String subject = "[Suporte] " + requestSubject;
		String htmlBody = "<p>Novo pedido de suporte enviado pelo app.</p>"
			+ "<ul>"
			+ "<li><b>Usuario:</b> " + escape(replyTo) + "</li>"
			+ "<li><b>Assunto:</b> " + escape(requestSubject) + "</li>"
			+ "</ul>"
			+ "<p><b>Mensagem:</b></p><pre style=\"white-space:pre-wrap\">" + escape(requestMessage)
			+ "</pre>";

		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
			helper.setFrom(fromEmail);
			helper.setTo(toEmail);
			if (replyTo != null && replyTo.contains("@")) {
				helper.setReplyTo(replyTo);
			}
			helper.setSubject(subject);
			helper.setText(htmlBody, true);
			mailSender.send(message);
		} catch (Exception e) {
			// O pedido ja esta persistido (aparece em /admin/support): apenas registramos a falha.
			LOG.error("Falha ao notificar o suporte ({}) sobre o pedido: {}", toEmail, e.getMessage(), e);
		}
	}

	private String escape(String value) {
		if (value == null) {
			return "";
		}
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}
}
