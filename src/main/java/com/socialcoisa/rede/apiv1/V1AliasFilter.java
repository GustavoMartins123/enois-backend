package com.socialcoisa.rede.apiv1;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * O alias versionado da API: <code>/api/v1/...</code> responde o mesmo que <code>/api/...</code>.
 *
 * <p>Até aqui a API tinha um endereco só, que muda junto com o produto: quem constrói um app em
 * cima do Énóis não tem como saber se a resposta de hoje é a de amanhã. O <code>/v1</code> é a
 * versão <b>fixada</b>: enquanto ela existir, o caminho e o formato do erro não mudam.
 *
 * <p>Como funciona: o filtro reescreve o caminho da requisição (tira o <code>/v1</code>) e
 * entrega o pedido ao mesmo handler do v0. Não há controller duplicado nem cópia da regra de
 * negócio — por construção a resposta do v1 é a do v0, que é o que o contrato promete.
 *
 * <p><b>Ordem importa:</b> o filtro roda antes dos limitadores de taxa ({@code @Order(1..3)}) para
 * que uma chamada pelo <code>/v1</code> caia na MESMA regra que a chamada pelo caminho sem
 * versão. Se o filtro rodasse depois, o login pelo <code>/v1</code> passaria por um caminho que o
 * limitador de força bruta não conhece — e o limite viraria uma porta dos fundos.
 *
 * <p>O prefixo do contexto (<code>/api</code> em producao, vazio nos testes) e preservado no
 * caminho reescrito: e ele que o contêiner usa para saber onde a aplicação começa.
 */
@Component
@Order(0)
class V1AliasFilter extends OncePerRequestFilter {

	/** Versão servida por este alias. */
	static final String VERSION = "v1";

	/** Caminho da versão, sem o prefixo do contexto. */
	static final String PREFIX = "/v1";

	/**
	 * O filtro NÃO escreve nenhum cabeçalho na resposta. A versão já está no caminho (e o índice
	 * {@code /api/v1} diz o que existe nela), e escrever cabeçalho aqui antes do corpo ser lido
	 * fez o upload grande (o ZIP do LinkedIn, que o container recusa no meio da stream) ser
	 * abortado com o cliente ainda enviando: o teste de upload quebrava com "pipe quebrado".
	 */
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String uri = request.getRequestURI();
		String context = contextPath(request);
		if (uri.equals(context + PREFIX)) {
			// O indice da versao: quem responde e o controller, o caminho fica como esta.
			filterChain.doFilter(request, response);
			return;
		}
		String rewritten = rewrite(uri, context);
		if (rewritten == null) {
			filterChain.doFilter(request, response);
			return;
		}
		filterChain.doFilter(new VersionedRequest(request, rewritten, context), response);
	}

	/**
	 * Caminho novo da requisição (com o prefixo do contexto) ou {@code null} quando a chamada não
	 * é do v1.
	 *
	 * <p>{@code /api/v1/feed/posts} vira {@code /api/feed/posts}; {@code /api/v1} (o índice da
	 * versão) e {@code /api/v1x} não são reescritos — o primeiro responde o índice, o segundo é um
	 * caminho que simplesmente não existe.
	 */
	static String rewrite(String uri, String context) {
		String base = context + PREFIX;
		if (uri == null || !uri.startsWith(base)) {
			return null;
		}
		String rest = uri.substring(base.length());
		if (rest.isEmpty() || !rest.startsWith("/")) {
			return null;
		}
		return context + rest;
	}

	private static String contextPath(HttpServletRequest request) {
		String context = request.getContextPath();
		return context == null ? "" : context;
	}

	/**
	 * A requisição com o caminho da versão removido. Os três métodos de caminho andam juntos
	 * ({@code getRequestURI}, {@code getServletPath} e {@code getPathInfo}): o roteador do Spring
	 * monta o caminho de busca a partir deles, e mexer em só um deixa o pedido sem rota.
	 */
	private static final class VersionedRequest extends HttpServletRequestWrapper {

		private final String uri;
		private final String context;

		VersionedRequest(HttpServletRequest request, String uri, String context) {
			super(request);
			this.uri = uri;
			this.context = context;
		}

		@Override
		public String getRequestURI() {
			return uri;
		}

		@Override
		public String getServletPath() {
			return uri.substring(context.length());
		}

		@Override
		public String getPathInfo() {
			return null;
		}

		@Override
		public StringBuffer getRequestURL() {
			StringBuffer url = super.getRequestURL();
			String original = url.toString();
			int v1 = original.indexOf(context + PREFIX);
			if (v1 < 0) {
				return url;
			}
			return new StringBuffer(original.substring(0, v1) + original.substring(v1 + PREFIX.length()));
		}
	}
}
