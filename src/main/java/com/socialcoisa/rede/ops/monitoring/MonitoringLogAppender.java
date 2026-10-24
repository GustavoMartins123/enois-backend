package com.socialcoisa.rede.ops.monitoring;

import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Captura os logs WARN/ERROR da aplicação (e o logger de auditoria) para o painel admin.
 *
 * <p>Fica no root logger por código, sem alterar o logback-spring.xml: o app continua mandando
 * tudo para o stdout/CloudWatch normalmente. O appender não pode logar nem capturar os próprios
 * logs — isso criaria um loop de escrita no banco.</p>
 */
@Component
public class MonitoringLogAppender extends AppenderBase<ILoggingEvent> {

	private static final String APP_PREFIX = "com.socialcoisa.rede";
	private static final String MONITORING_PREFIX = "com.socialcoisa.rede.ops.monitoring";
	private static final ThreadLocal<Boolean> IN_APPENDER = new ThreadLocal<>();

	private final MonitoringService monitoring;
	private ch.qos.logback.classic.Logger attachedTo;

	MonitoringLogAppender(MonitoringService monitoring) {
		this.monitoring = monitoring;
	}

	@PostConstruct
	void attach() {
		LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
		setContext(context);
		setName("monitoring-panel");
		start();
		attachedTo = context.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
		attachedTo.addAppender(this);
	}

	@PreDestroy
	void detach() {
		if (attachedTo != null) {
			attachedTo.detachAppender(this);
		}
		stop();
	}

	@Override
	protected void append(ILoggingEvent event) {
		if (IN_APPENDER.get() != null || event == null) {
			return;
		}
		String logger = event.getLoggerName();
		if (logger == null || logger.startsWith(MONITORING_PREFIX)
				|| logger.startsWith("com.socialcoisa.rede.shared.web.RequestObservabilityFilter")
				|| logger.startsWith("com.socialcoisa.rede.shared.web.GlobalExceptionHandler")) {
			return;
		}
		boolean audit = "audit".equals(logger);
		boolean appLog = logger.startsWith(APP_PREFIX);
		if (!appLog && !audit) {
			return;
		}
		boolean important = event.getLevel().isGreaterOrEqual(ch.qos.logback.classic.Level.WARN) || audit;
		if (!important) {
			return;
		}
		IN_APPENDER.set(Boolean.TRUE);
		try {
			monitoring.recordLog(event.getLevel().toString(), logger, event.getFormattedMessage(),
				MDC.get("requestId"), MDC.get("userId"), MDC.get("http.path"));
		}
		catch (RuntimeException ignored) {
			// Log não pode derrubar a thread que estava logando.
		}
		finally {
			IN_APPENDER.remove();
		}
	}
}
