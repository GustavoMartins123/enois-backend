package com.socialcoisa.rede.notifications.adapters.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import com.socialcoisa.rede.shared.web.TestAuthHelper;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationContractControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private org.springframework.jdbc.core.JdbcTemplate jdbc;

	private TestAuthHelper auth;

	@BeforeEach
	void setUp() {
		auth = new TestAuthHelper(mockMvc, objectMapper);
	}

	@Test
	void listsNotificationsAndMarksAllRead() throws Exception {
		var session = auth.authenticate("notif_user");

		// Sem conteudo, so o contrato do array vazio.
		mockMvc.perform(get("/notifications")
				.header("X-Session-Token", session.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items").isArray());

		mockMvc.perform(post("/notifications/read-all")
				.header("X-Session-Token", session.token())
				.header("Idempotency-Key", "notif-read-all-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ALL_READ"));
	}

	/**
	 * "Ler tudo" tem que GRAVAR: o update em massa roda dentro de transacao de leitura se ninguem
	 * anotar o servico, e a queixa de 23/10 foi exatamente essa (responde ALL_READ e as
	 * notificacoes continuavam nao-lidas).
	 */
	@Test
	void readAllPersistsReadFlagOnExistingNotifications() throws Exception {
		var session = auth.authenticate("notif_all_user");
		var userId = session.userId();

		jdbc.update("""
				insert into notifications (id, user_id, kind, actor_id, title, body, `read`, created_at)
				values (?, ?, 'SYSTEM', 'sistema', 'Aviso 1', 'corpo', false, current_timestamp),
				       (?, ?, 'SYSTEM', 'sistema', 'Aviso 2', 'corpo', false, current_timestamp)
				""", "notif_all_1", userId, "notif_all_2", userId);

		assertThat(jdbc.queryForObject(
			"select count(*) from notifications where user_id = ? and `read` = false", Integer.class, userId))
			.isEqualTo(2);

		mockMvc.perform(post("/notifications/read-all")
				.header("X-Session-Token", session.token())
				.header("Idempotency-Key", "notif-read-all-persist"))
			.andExpect(status().isOk());

		assertThat(jdbc.queryForObject(
			"select count(*) from notifications where user_id = ? and `read` = false", Integer.class, userId))
			.as("todas as notificacoes devem ficar lidas no banco")
			.isZero();
	}
}
