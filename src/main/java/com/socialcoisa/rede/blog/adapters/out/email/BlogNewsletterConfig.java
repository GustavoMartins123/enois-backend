package com.socialcoisa.rede.blog.adapters.out.email;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Executor da newsletter do blog: uma thread (SMTP e I/O), fila limitada e thread daemon — o
 * publish entrega o e-mail para a fila e responde; fila cheia adia (nunca segura o request).
 */
@Configuration(proxyBeanMethods = false)
class BlogNewsletterConfig {

	@Bean(destroyMethod = "shutdown")
	Executor blogNewsletterExecutor() {
		return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(200),
			runnable -> {
				Thread thread = new Thread(runnable, "blog-newsletter");
				thread.setDaemon(true);
				return thread;
			},
			new ThreadPoolExecutor.AbortPolicy());
	}
}
