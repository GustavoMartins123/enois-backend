package com.socialcoisa.rede.games.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Regras do endereço do jogo.
 *
 * O link vem colado pelo criador: pode ter acento e caractere especial no caminho (o parser do
 * URI.create recusa), e precisa continuar valendo o essencial — só https, sem credenciais na URL e
 * host que resolve para endereço público.
 */
class GameUrlGuardTests {

	private final GameUrlGuard guard = new GameUrlGuard();

	@Test
	void aceitaHttpsSimples() {
		assertThat(guard.validate("https://example.com/jogo")).isEqualTo("https://example.com/jogo");
	}

	@Test
	void codificaAcentoNoCaminhoEmVezDeRecusar() {
		assertThat(guard.validate("https://example.com/cafés-da-manhã")).contains("caf%C3%A9");
	}

	@Test
	void codificaCaractereEspecialNaQuery() {
		assertThat(guard.validate("https://example.com/j?nome=maçã")).contains("ma%C3%A3");
	}

	@Test
	void recusaHttpPuro() {
		assertThatThrownBy(() -> guard.validate("http://example.com/jogo"))
			.isInstanceOf(ApiException.class)
			.hasMessageContaining("https");
	}

	@Test
	void recusaCredenciaisNaUrl() {
		assertThatThrownBy(() -> guard.validate("https://user:pass@example.com/jogo"))
			.isInstanceOf(ApiException.class)
			.hasMessageContaining("usuário e senha");
	}

	@Test
	void recusaEnderecoInterno() {
		assertThatThrownBy(() -> guard.validate("https://localhost/jogo"))
			.isInstanceOf(ApiException.class)
			.hasMessageContaining("interno");
	}

	@Test
	void recusaEspacoNoEndereco() {
		assertThatThrownBy(() -> guard.validate("https://example.com/jogo demo"))
			.isInstanceOf(ApiException.class)
			.hasMessageContaining("Endereço inválido");
	}

	@Test
	void recusaEsquemaEstranho() {
		assertThatThrownBy(() -> guard.validate("ftp://example.com/jogo"))
			.isInstanceOf(ApiException.class)
			.hasMessageContaining("https");
	}
}
