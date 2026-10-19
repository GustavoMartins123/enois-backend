package com.socialcoisa.rede.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;

import com.socialcoisa.rede.coin.application.CoinService;
import com.socialcoisa.rede.notifications.application.MediaRecoveryApologyWorker;
import com.socialcoisa.rede.notifications.application.NotificationService;
import com.socialcoisa.rede.notifications.application.port.out.SystemAnnouncementRepository;
import com.socialcoisa.rede.shared.web.TestAuthHelper;

/**
 * O comunicado de desculpas só sai quando o S3 está configurado (upload assinado disponível),
 * uma única vez, com 100 faíscas idempotentes por usuário e notificação para cada um.
 */
@SpringBootTest(properties = {"rede.media.storage=s3", "rede.media.s3.bucket=bucket-de-teste",
		"rede.media.s3.publicBaseUrl=https://cdn.exemplo.com"})
@AutoConfigureMockMvc
class MediaRecoveryApologyTests {

	static {
		System.setProperty("aws.accessKeyId", "test");
		System.setProperty("aws.secretAccessKey", "test");
	}

	@Autowired
	private MediaRecoveryApologyWorker worker;

	@Autowired
	private CoinService coins;

	@Autowired
	private NotificationService notifications;

	@Autowired
	private SystemAnnouncementRepository announcements;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void announcesOnceWithBonusAndNotification() throws Exception {
		var auth = new TestAuthHelper(mockMvc, objectMapper);
		var pessoaA = auth.authenticate("apology_user_a");
		var pessoaB = auth.authenticate("apology_user_b");

		worker.announceIfStorageRecovered();

		for (var pessoa : List.of(pessoaA, pessoaB)) {
			String ref = MediaRecoveryApologyWorker.ANNOUNCEMENT_KEY + ":" + pessoa.userId();
			assertThat(coins.hasReward(ref)).isTrue();
			assertThat(coins.balance(pessoa.userId())).isGreaterThanOrEqualTo(MediaRecoveryApologyWorker.BONUS);
			assertThat(notifications.list(pessoa.userId(), null, 50))
				.anyMatch(aviso -> MediaRecoveryApologyWorker.TITULO.equals(aviso.title()));
		}
		assertThat(announcements.isSent(MediaRecoveryApologyWorker.ANNOUNCEMENT_KEY)).isTrue();

		// Rodar de novo não duplica: marcador de comunicado + ref idempotente da recompensa.
		long saldoAntes = coins.balance(pessoaA.userId());
		int avisosAntes = notifications.list(pessoaA.userId(), null, 50).size();
		worker.announceIfStorageRecovered();
		assertThat(coins.balance(pessoaA.userId())).isEqualTo(saldoAntes);
		assertThat(notifications.list(pessoaA.userId(), null, 50)).hasSize(avisosAntes);
	}
}
