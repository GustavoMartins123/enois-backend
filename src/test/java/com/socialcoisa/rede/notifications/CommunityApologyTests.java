package com.socialcoisa.rede.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.socialcoisa.rede.coin.application.CoinService;
import com.socialcoisa.rede.notifications.application.CommunityApologyWorker;
import com.socialcoisa.rede.notifications.application.NotificationService;
import com.socialcoisa.rede.notifications.application.port.out.SystemAnnouncementRepository;
import com.socialcoisa.rede.shared.web.TestAuthHelper;

import tools.jackson.databind.ObjectMapper;

/**
 * Desculpas públicas: 100 faíscas por conta e uma notificação, uma única vez — reiniciar/rodar
 * de novo não duplica crédito nem aviso.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CommunityApologyTests {

	@Autowired
	private CommunityApologyWorker worker;

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
	void grantsTheBonusAndNotifiesEachAccountOnce() throws Exception {
		var auth = new TestAuthHelper(mockMvc, objectMapper);
		var pessoaA = auth.authenticate("apology_community_a");
		var pessoaB = auth.authenticate("apology_community_b");

		worker.announce();

		for (var pessoa : List.of(pessoaA, pessoaB)) {
			String ref = CommunityApologyWorker.ANNOUNCEMENT_KEY + ":" + pessoa.userId();
			assertThat(coins.hasReward(ref)).isTrue();
			assertThat(coins.balance(pessoa.userId())).isGreaterThanOrEqualTo(CommunityApologyWorker.BONUS);
			assertThat(notifications.list(pessoa.userId(), null, 50))
				.anyMatch(aviso -> CommunityApologyWorker.TITULO.equals(aviso.title()));
		}
		assertThat(announcements.isSent(CommunityApologyWorker.ANNOUNCEMENT_KEY)).isTrue();

		long saldoAntes = coins.balance(pessoaA.userId());
		int avisosAntes = notifications.list(pessoaA.userId(), null, 50).size();
		worker.announce();
		assertThat(coins.balance(pessoaA.userId())).isEqualTo(saldoAntes);
		assertThat(notifications.list(pessoaA.userId(), null, 50)).hasSize(avisosAntes);
	}
}
