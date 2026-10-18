package com.socialcoisa.rede.games.application;

import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Valida o endereço onde o jogo está publicado.
 *
 * **O Énóis não busca essa URL** — quem carrega é o navegador de quem joga, dentro do quadro. Isso
 * muda o que a validação protege: não é o servidor que pode ser induzido a falar com a rede
 * interna, é a MÁQUINA DE QUEM LÊ (um jogo apontando para `http://192.168.0.1/` faria o navegador da
 * pessoa bater no roteador dela). Por isso a checagem é de destino, não de conteúdo: só `https`, sem
 * credenciais na URL, e o host precisa resolver para endereço PÚBLICO.
 *
 * O que fica de fora de propósito: nada de seguir redirecionamento (não buscamos nada), nada de
 * julgar se o jogo é bom, e nada de contornar bloqueio de incorporação do site — se o destino
 * proibir quadro, a página diz isso e oferece abrir na origem.
 */
@Component
public class GameUrlGuard {

	private static final int MAX_URL = 500;

	/** Devolve a URL normalizada ou lança 400 explicando o motivo. */
	public String validate(String rawUrl) {
		String value = rawUrl == null ? "" : rawUrl.trim();
		if (!StringUtils.hasText(value)) {
			throw invalid("Informe o endereço do jogo.");
		}
		if (value.length() > MAX_URL) {
			throw invalid("Endereço longo demais.");
		}
		if (StringUtils.containsWhitespace(value)) {
			throw invalid("Endereço inválido.");
		}

		URI uri;
		try {
			uri = URI.create(value);
		}
		catch (IllegalArgumentException e) {
			throw invalid("Endereço inválido.");
		}

		String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
		if (!"https".equals(scheme)) {
			throw invalid("O jogo precisa ser servido em https.");
		}
		if (uri.getUserInfo() != null) {
			throw invalid("Endereço não pode levar usuário e senha.");
		}
		String host = uri.getHost();
		if (!StringUtils.hasText(host)) {
			throw invalid("Endereço sem domínio.");
		}

		requirePublicHost(host);
		return uri.toString();
	}

	/**
	 * O host precisa resolver para endereço público. Cobre loopback, faixas privadas (IPv4 e IPv6),
	 * link-local (onde vive o serviço de metadados da nuvem) e multicast — e recusa host que não
	 * resolve, porque endereço que não existe não é jogo.
	 */
	private void requirePublicHost(String host) {
		String normalized = host.toLowerCase(Locale.ROOT);
		if (normalized.equals("localhost") || normalized.endsWith(".localhost") || normalized.endsWith(".local")
				|| normalized.endsWith(".internal")) {
			throw invalid("Endereço interno não é aceito.");
		}

		InetAddress[] addresses;
		try {
			addresses = InetAddress.getAllByName(host);
		}
		catch (Exception e) {
			throw invalid("Não foi possível resolver o endereço do jogo.");
		}
		if (addresses.length == 0) {
			throw invalid("Não foi possível resolver o endereço do jogo.");
		}
		for (InetAddress address : addresses) {
			if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isLinkLocalAddress()
					|| address.isSiteLocalAddress() || address.isMulticastAddress()) {
				throw invalid("Endereço de rede interna não é aceito.");
			}
			byte[] bytes = address.getAddress();
			if (bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc) {
				throw invalid("Endereço de rede interna não é aceito."); // faixa fc00::/7 (IPv6 privada)
			}
		}
	}

	private ApiException invalid(String message) {
		return new ApiException(400, "INVALID_GAME_URL", message);
	}
}
