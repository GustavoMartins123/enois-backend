package com.socialcoisa.rede.linkedin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.socialcoisa.rede.linkedin.application.port.out.LinkedInConnectionRepository;
import com.socialcoisa.rede.linkedin.application.port.out.LinkedInShareClient;
import com.socialcoisa.rede.linkedin.domain.LinkedInConnection;
import com.socialcoisa.rede.shared.security.SecretCipher;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * A integracao de publicacao no LinkedIn no nivel da regra:
 *
 * - token e guardado CIFRADO e nunca em claro;
 * - sem a chave de criptografia nada e gravado e o status explica o motivo;
 * - a preferencia so liga com conexao valida; token vencido pede reconexao;
 * - o share leva o texto do post e o link publico, cabendo no limite de 3.000;
 * - sem preferencia (ou sem token) nada e publicado.
 */
class LinkedInIntegrationServiceTests {

	private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");
	private static final String BASE = "https://enois.ia.br";

	private final SecretCipher cipher = new SecretCipher(SecretCipher.generateKeyBase64());
	private final InMemoryRepository repository = new InMemoryRepository();
	private final FakeShareClient client = new FakeShareClient();

	private LinkedInIntegrationService service() {
		return new LinkedInIntegrationService(repository, client, cipher, Clock.fixed(NOW, ZoneOffset.UTC), BASE);
	}

	@Test
	void tokenFicaCifradoENaoEmClaro() {
		LinkedInIntegrationService service = service();

		service.remember("u1", "sub-1", "token-secreto", NOW.plusSeconds(3600));

		LinkedInConnection stored = repository.findByUserId("u1").orElseThrow();
		assertThat(stored.accessTokenEnc()).isNotEqualTo("token-secreto");
		assertThat(cipher.decrypt(stored.accessTokenEnc())).isEqualTo("token-secreto");

		var status = service.status("u1");
		assertThat(status.connected()).isTrue();
		assertThat(status.canPost()).isTrue();
		assertThat(status.postSync()).isFalse();
		assertThat(status.reason()).isNull();
	}

	@Test
	void semChaveDeCriptografiaNaoGuardaToken() {
		LinkedInIntegrationService service = new LinkedInIntegrationService(repository, client,
			new SecretCipher(""), Clock.fixed(NOW, ZoneOffset.UTC), BASE);

		service.remember("u1", "sub-1", "token-secreto", NOW.plusSeconds(3600));

		assertThat(repository.findByUserId("u1")).isEmpty();
		assertThat(service.status("u1").reason()).isEqualTo("NOT_CONNECTED");
	}

	@Test
	void statusExplicaTokenVencidoESemChave() {
		service().remember("u1", "sub-1", "token", NOW.minusSeconds(60));
		assertThat(service().status("u1").canPost()).isFalse();
		assertThat(service().status("u1").reason()).isEqualTo("TOKEN_EXPIRED");

		// Linha existente mas servidor sem chave: conectado, porem nao pode publicar.
		InMemoryRepository outro = new InMemoryRepository();
		outro.save(new LinkedInConnection("u2", "sub-2", "qualquer", NOW.plusSeconds(3600), false, NOW, NOW));
		LinkedInIntegrationService semChave = new LinkedInIntegrationService(outro, client, new SecretCipher(""),
			Clock.fixed(NOW, ZoneOffset.UTC), BASE);
		assertThat(semChave.status("u2").connected()).isTrue();
		assertThat(semChave.status("u2").canPost()).isFalse();
		assertThat(semChave.status("u2").reason()).isEqualTo("SERVER_KEY_MISSING");
	}

	@Test
	void preferenciaSoLigaComConexaoValida() {
		LinkedInIntegrationService service = service();
		assertThatThrownBy(() -> service.setPostSync("u1", true))
			.isInstanceOfSatisfying(ApiException.class,
				exception -> assertThat(exception.code()).isEqualTo("LINKEDIN_NOT_CONNECTED"));

		service.remember("u1", "sub-1", "token", NOW.minusSeconds(10));
		assertThatThrownBy(() -> service.setPostSync("u1", true))
			.isInstanceOfSatisfying(ApiException.class,
				exception -> assertThat(exception.code()).isEqualTo("LINKEDIN_NOT_READY"));

		service.remember("u1", "sub-1", "token", NOW.plusSeconds(3600));
		assertThat(service.setPostSync("u1", true).postSync()).isTrue();
		assertThat(service.setPostSync("u1", false).postSync()).isFalse();
	}

	@Test
	void shareLevaTextoELinkPublico() {
		LinkedInIntegrationService service = service();
		service.remember("u1", "sub-1", "token-abc", NOW.plusSeconds(3600));
		service.setPostSync("u1", true);

		service.sharePublishedPost("u1", "post_1", "Meu texto", "Publicação no Énóis");

		assertThat(client.calls).hasSize(1);
		FakeShareClient.Call call = client.calls.get(0);
		assertThat(call.accessToken).isEqualTo("token-abc");
		assertThat(call.memberSub).isEqualTo("sub-1");
		assertThat(call.commentary).contains("Meu texto").contains(BASE + "/post/post_1");
		assertThat(call.url).isEqualTo(BASE + "/post/post_1");
	}

	@Test
	void semPreferenciaOuSemTokenNaoPublica() {
		LinkedInIntegrationService service = service();
		service.remember("u1", "sub-1", "token", NOW.plusSeconds(3600));
		service.sharePublishedPost("u1", "post_1", "texto", "t");
		assertThat(client.calls).isEmpty();

		service.setPostSync("u1", true);
		service.remember("u1", "sub-1", "token", NOW.minusSeconds(1));
		service.sharePublishedPost("u1", "post_1", "texto", "t");
		assertThat(client.calls).isEmpty();
	}

	@Test
	void textoLongoCabeNoLimiteDoLinkedIn() {
		String url = BASE + "/post/post_1";
		String generated = LinkedInIntegrationService.commentary("a".repeat(10_000), url);

		assertThat(generated.length()).isLessThanOrEqualTo(3_000);
		assertThat(generated).endsWith(url);
		assertThat(LinkedInIntegrationService.commentary("", url)).isEqualTo(url);
	}

	private static final class InMemoryRepository implements LinkedInConnectionRepository {
		private final Map<String, LinkedInConnection> byUser = new HashMap<>();

		@Override
		public Optional<LinkedInConnection> findByUserId(String userId) {
			return Optional.ofNullable(byUser.get(userId));
		}

		@Override
		public void save(LinkedInConnection connection) {
			byUser.put(connection.userId(), connection);
		}

		@Override
		public void deleteByUserId(String userId) {
			byUser.remove(userId);
		}
	}

	private static final class FakeShareClient implements LinkedInShareClient {
		record Call(String accessToken, String memberSub, String commentary, String url, String title) {
		}

		private final java.util.List<Call> calls = new java.util.ArrayList<>();

		@Override
		public boolean share(String accessToken, String memberSub, String commentary, String url, String title) {
			calls.add(new Call(accessToken, memberSub, commentary, url, title));
			return true;
		}
	}
}
