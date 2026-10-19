package com.socialcoisa.rede.identity.application;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.socialcoisa.rede.ops.application.port.out.EmailLogRepository;
import com.socialcoisa.rede.ops.domain.EmailLog;
import com.socialcoisa.rede.ops.domain.EmailLogStatus;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    private static final Logger LOG = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final String frontendUrl;
    private final String fromEmail;
    private final EmailLogRepository emailLogs;

    public EmailService(
            JavaMailSender mailSender,
            TemplateEngine templateEngine,
            @Value("${rede.frontend.url}") String frontendUrl,
            @Value("${spring.mail.username}") String fromEmail,
            EmailLogRepository emailLogs) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.frontendUrl = frontendUrl.endsWith("/")
                ? frontendUrl.substring(0, frontendUrl.length() - 1)
                : frontendUrl;
        this.fromEmail = fromEmail;
        this.emailLogs = emailLogs;
    }

    /**
     * Registra o metadado do envio sem nunca atrapalhar o envio em si: se o log falhar, o e-mail
     * (que é o que importa) segue normalmente.
     */
    private void recordEmail(String toEmail, String subject, String kind, boolean sent, String error) {
        try {
            emailLogs.save(EmailLog.create("email_" + UUID.randomUUID().toString().replace("-", ""),
                truncate(toEmail, 180), truncate(subject, 200), kind,
                sent ? EmailLogStatus.SENT : EmailLogStatus.FAILED, truncate(error, 500), Instant.now()));
        } catch (RuntimeException logFailure) {
            LOG.warn("Nao foi possivel registrar o envio de e-mail para {}: {}", toEmail,
                logFailure.getMessage());
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
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
            recordEmail(toEmail, subject, "PASSWORD_RESET", true, null);
        } catch (Exception e) {
            recordEmail(toEmail, subject, "PASSWORD_RESET", false, e.getMessage());
            LOG.error("Falha ao enviar email de reset para {}: {}", toEmail, e.getMessage(), e);
        }
    }

    /**
     * Envia o link de confirmacao do e-mail do cadastro.
     *
     * @return false quando o envio falha — a conta continua pendente e a tela oferece reenviar,
     *         em vez de deixar o usuario esperando um e-mail que nunca chegou
     */
    public boolean sendVerificationEmail(String toEmail, String userName, String verificationToken) {
        Context context = new Context();
        context.setVariable("userName", userName);
        context.setVariable("verificationLink", buildVerificationLink(verificationToken));
        context.setVariable("currentYear", java.time.Year.now().getValue());

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Confirme seu e-mail no Énóis");
            helper.setText(templateEngine.process("email-verification", context), true);
            mailSender.send(message);
            recordEmail(toEmail, "Confirme seu e-mail no Énóis", "EMAIL_VERIFICATION", true, null);
            return true;
        } catch (Exception e) {
            recordEmail(toEmail, "Confirme seu e-mail no Énóis", "EMAIL_VERIFICATION", false, e.getMessage());
            LOG.error("Falha ao enviar email de confirmacao para {}: {}", toEmail, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Codigo do segundo fator por e-mail.
     *
     * @return false quando o envio falha — o login com 2FA precisa saber que o codigo nao saiu, em
     *         vez de deixar o usuario tentando um codigo que nunca chegou
     */
    public boolean sendTwoFactorCode(String toEmail, String userName, String code) {
        Context context = new Context();
        context.setVariable("userName", userName);
        context.setVariable("code", code);
        context.setVariable("currentYear", java.time.Year.now().getValue());

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Seu código de acesso: " + code);
            helper.setText(templateEngine.process("two-factor-code", context), true);
            mailSender.send(message);
            recordEmail(toEmail, "Seu código de acesso: " + code, "TWO_FACTOR", true, null);
            return true;
        } catch (Exception e) {
            recordEmail(toEmail, "Seu código de acesso: " + code, "TWO_FACTOR", false, e.getMessage());
            LOG.error("Falha ao enviar o codigo do segundo fator para {}: {}", toEmail, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Envio de um HTML ja montado pelo chamador.
     *
     * Existe para modulos que precisam de um e-mail proprio (ex.: candidatura de vaga) sem que a
     * identidade precise conhecer o dominio deles.
     *
     * @return false quando o envio falha (quem chamou decide o que fazer; o dado ja esta persistido)
     */
    public boolean sendHtml(String toEmail, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            recordEmail(toEmail, subject, "HTML", true, null);
            return true;
        } catch (Exception e) {
            recordEmail(toEmail, subject, "HTML", false, e.getMessage());
            LOG.error("Falha ao enviar email para {}: {}", toEmail, e.getMessage(), e);
            return false;
        }
    }

    private String buildResetLink(String resetToken) {
        return frontendUrl + "/auth/reset?token=" + resetToken;
    }

    private String buildVerificationLink(String verificationToken) {
        return frontendUrl + "/auth/verify?token=" + verificationToken;
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
		sendSupportRequestEmail(toEmail, requestSubject, requestMessage, replyTo, null, null);
	}

	/**
	 * Suporte com ANEXO DE IMAGEM: o print que a pessoa juntou ao pedido. A imagem vem
	 * como bytes (baixados do CDN pelo chamador); sem imagem, cai no e-mail simples.
	 */
	public void sendSupportRequestEmail(String toEmail, String requestSubject, String requestMessage, String replyTo,
			byte[] imageBytes, String imageName) {
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
			helper.setSubject("[Suporte] " + requestSubject);
			helper.setText(htmlBody, true);
			if (imageBytes != null && imageBytes.length > 0) {
				helper.addAttachment(imageName == null ? "anexo.png" : imageName,
					new org.springframework.core.io.ByteArrayResource(imageBytes));
			}
			mailSender.send(message);
			recordEmail(toEmail, "[Suporte] " + requestSubject, "SUPPORT", true, null);
		} catch (Exception e) {
			recordEmail(toEmail, "[Suporte] " + requestSubject, "SUPPORT", false, e.getMessage());
			// O pedido ja esta persistido (aparece em /admin/support): apenas registramos a falha.
			LOG.error("Falha ao notificar o suporte ({}) sobre o pedido: {}", toEmail, e.getMessage(), e);
		}
	}

	/** E-mail HTML pronto (o chamador escapa o conteudo do usuario). Best-effort: nunca lanca. */
	public void sendHtmlEmail(String toEmail, String subject, String htmlBody) {
		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
			helper.setFrom(fromEmail);
			helper.setTo(toEmail);
			helper.setSubject(subject);
			helper.setText(htmlBody, true);
			mailSender.send(message);
			recordEmail(toEmail, subject, "HTML", true, null);
		} catch (Exception e) {
			recordEmail(toEmail, subject, "HTML", false, e.getMessage());
			LOG.error("Falha ao enviar e-mail para {}: {}", toEmail, e.getMessage(), e);
		}
	}

	private String escape(String value) {
		if (value == null) {
			return "";
		}
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}
}
