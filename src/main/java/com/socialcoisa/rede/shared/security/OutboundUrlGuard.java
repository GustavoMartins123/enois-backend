package com.socialcoisa.rede.shared.security;

import java.net.InetAddress;
import java.net.URI;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Guarda contra SSRF: valida uma URL **fornecida pelo usuário** antes de o backend buscar o
 * conteúdo dela. Um anexo de suporte apontando para `http://169.254.169.254/...` (metadados da
 * nuvem), para um serviço interno do cluster ou para o banco não pode virar requisição nossa.
 *
 * Regras: só http/https, só portas padrão (80/443), host que não seja literal de loopback/
 * faixa privada/link-local e cujo DNS resolva apenas para endereços públicos. Falha é negativa:
 * quem chama trata como "não busca".
 */
@Component
public class OutboundUrlGuard {

	private static final Logger LOG = LoggerFactory.getLogger(OutboundUrlGuard.class);

	private static final Set<String> ALLOWED_PORTS = Set.of("80", "443", "-1", "");

	public boolean safe(String url) {
		if (url == null || url.isBlank() || url.length() > 2000) {
			return false;
		}
		URI uri;
		try {
			uri = URI.create(url.trim());
		}
		catch (RuntimeException e) {
			return false;
		}
		String scheme = uri.getScheme();
		if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
			return false;
		}
		if (!ALLOWED_PORTS.contains(String.valueOf(uri.getPort()))) {
			return false;
		}
		String host = uri.getHost();
		if (host == null || host.isBlank()) {
			return false;
		}
		String lower = host.toLowerCase();
		if (lower.equals("localhost") || lower.endsWith(".localhost") || lower.endsWith(".internal")
			|| lower.endsWith(".local") || lower.equals("metadata.google.internal")) {
			return false;
		}
		try {
			// Resolução cobre nome e literal: um IP público escrito direto também passa por aqui.
			return InetAddress.getAllByName(host).length > 0
				&& java.util.Arrays.stream(InetAddress.getAllByName(host)).allMatch(OutboundUrlGuard::isPublic);
		}
		catch (Exception e) {
			LOG.debug("OutboundUrlGuard: host {} nao resolveu: {}", host, e.getMessage());
			return false;
		}
	}

	private static boolean isPublic(InetAddress address) {
		if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
			|| address.isSiteLocalAddress() || address.isMulticastAddress()) {
			return false;
		}
		byte[] bytes = address.getAddress();
		if (bytes.length == 4) {
			return !isPrivateV4(bytes);
		}
		if (bytes.length == 16) {
			return !isPrivateV6(bytes);
		}
		return false;
	}

	private static boolean isPrivateV4(byte[] b) {
		int first = b[0] & 0xFF;
		int second = b[1] & 0xFF;
		// 10/8, 172.16/12, 192.168/16, 100.64/10 (CGNAT), 0/8 e 198.18/15 (bench)
		return first == 10 || (first == 172 && second >= 16 && second <= 31) || (first == 192 && second == 168)
			|| (first == 100 && second >= 64 && second <= 127) || first == 0 || first == 198 || first == 127;
	}

	private static boolean isPrivateV6(byte[] b) {
		// fc00::/7 (unique local), fe80::/10 (link local), ::1 e mapeamento IPv4 (::ffff:x.x.x.x)
		if ((b[0] & 0xFE) == 0xFC || (b[0] == 0xFE && (b[1] & 0xC0) == 0x80)) {
			return true;
		}
		boolean allZero = true;
		for (int i = 0; i < 15; i++) {
			if (b[i] != 0) {
				allZero = false;
				break;
			}
		}
		if (allZero && b[15] == 1) {
			return true; // ::1
		}
		boolean v4Mapped = true;
		for (int i = 0; i < 10; i++) {
			if (b[i] != 0) {
				v4Mapped = false;
				break;
			}
		}
		if (v4Mapped && b[10] == (byte) 0xFF && b[11] == (byte) 0xFF) {
			return isPrivateV4(new byte[] { b[12], b[13], b[14], b[15] });
		}
		return false;
	}
}
