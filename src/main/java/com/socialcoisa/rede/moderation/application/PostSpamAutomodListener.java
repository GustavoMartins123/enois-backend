package com.socialcoisa.rede.moderation.application;

import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.socialcoisa.rede.feed.application.PostPublishedEvent;

/**
 * Escuta a publicação de posts e dispara o automod de spam FORA da transação.
 *
 * Duas garantias: o listener roda só DEPOIS do commit (post que sofreu rollback não é analisado) e
 * em outra thread — a análise pode chamar a IA e não pode atrasar o "Publicar".
 */
@Component
class PostSpamAutomodListener {

	private static final Logger LOG = LoggerFactory.getLogger(PostSpamAutomodListener.class);

	private final PostSpamAutomodService automod;
	private final Executor executor;

	PostSpamAutomodListener(PostSpamAutomodService automod,
			@Qualifier("spamAutomodExecutor") Executor executor) {
		this.automod = automod;
		this.executor = executor;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onPostPublished(PostPublishedEvent event) {
		executor.execute(() -> {
			try {
				automod.evaluate(event.authorId());
			}
			catch (RuntimeException failure) {
				// O automod é melhor esforço: falha aqui nunca pode afetar a publicação (que já está
				// no ar) nem derrubar a thread do executor silenciosamente.
				LOG.warn("automod_spam_falhou post={}: {}", event.postId(), failure.getMessage());
			}
		});
	}
}
