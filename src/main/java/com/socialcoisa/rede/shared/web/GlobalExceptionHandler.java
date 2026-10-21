package com.socialcoisa.rede.shared.web;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ErrorResponse> handleApiException(ApiException exception) {
		return ResponseEntity.status(exception.status())
			.body(new ErrorResponse(exception.code(), exception.getMessage(), Instant.now()));
	}

	/**
	 * Cabeçalho de sessão ausente é 401, não 400. Semanticamente a rota está protegida e o cliente
	 * não se autenticou; devolver BAD_REQUEST fazia o front tratar como requisição malformada e
	 * escondia a exigência de login dos monitores. Outros cabeçalhos obrigatórios continuam 400.
	 */
	@ExceptionHandler(MissingRequestHeaderException.class)
	ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException exception) {
		if ("X-Session-Token".equalsIgnoreCase(exception.getHeaderName())) {
			return ResponseEntity.status(401)
				.body(new ErrorResponse("UNAUTHENTICATED", "Sessao invalida ou expirada.", Instant.now()));
		}
		return ResponseEntity.badRequest()
			.body(new ErrorResponse("BAD_REQUEST", "Requisicao invalida.", Instant.now()));
	}

	// InvalidDataAccessApiUsageException cobre, por exemplo, PageRequest com size < 1 (limit=0).
	@ExceptionHandler({ MethodArgumentNotValidException.class,
			HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
			IllegalArgumentException.class, InvalidDataAccessApiUsageException.class,
			MissingServletRequestParameterException.class })
	ResponseEntity<ErrorResponse> handleBadRequest(Exception exception) {
		return ResponseEntity.badRequest()
			.body(new ErrorResponse("BAD_REQUEST", "Requisicao invalida.", Instant.now()));
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<ErrorResponse> handleConflict(DataIntegrityViolationException exception) {
		// A resposta e generica de proposito (nao vaza detalhe de banco para quem chamou), mas o log
		// guarda a causa raiz. Foi um "Data too long for column 'page_html'" invisivel que virou
		// horas de investigacao: o 409 sozinho nao diz se foi coluna pequena, chave duplicada ou FK.
		LOGGER.warn("data_integrity_violation: {}", rootMessage(exception));
		return ResponseEntity.status(409)
			.body(new ErrorResponse("CONFLICT", "Operacao conflita com o estado atual dos dados.", Instant.now()));
	}

	@ExceptionHandler({ ConcurrencyFailureException.class, jakarta.persistence.OptimisticLockException.class })
	ResponseEntity<ErrorResponse> handleConcurrentWrite(Exception exception) {
		return ResponseEntity.status(409).body(new ErrorResponse("CONCURRENT_WRITE",
			"Outra operacao alterou estes dados. Tente novamente.", Instant.now()));
	}

	/** A mensagem mais especifica da cadeia: o "Data too long for column ..." mora no fim dela. */
	private String rootMessage(Throwable exception) {
		Throwable cause = exception;
		while (cause.getCause() != null) {
			cause = cause.getCause();
		}
		return cause.getMessage();
	}

	/**
	 * O arquivo estourou o limite do Servlet (ver `spring.servlet.multipart.max-file-size`). Sem
	 * este tratamento, um ZIP grande virava "Erro interno inesperado" — foi assim que o import do
	 * LinkedIn (5 MB no servico, 1 MB no Servlet) ficou sem resposta util para quem tentava.
	 */
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ResponseEntity<ErrorResponse> handleUploadTooLarge(MaxUploadSizeExceededException exception) {
		return ResponseEntity.status(413)
			.body(new ErrorResponse("FILE_TOO_LARGE", "O arquivo enviado passa do limite permitido.",
				Instant.now()));
	}

	@ExceptionHandler(RequestNotPermitted.class)
	ResponseEntity<ErrorResponse> handleRateLimit(RequestNotPermitted exception) {
		return ResponseEntity.status(429)
			.body(new ErrorResponse("RATE_LIMITED", "Limite de requisicoes excedido.", Instant.now()));
	}

	@ExceptionHandler({ CallNotPermittedException.class, BulkheadFullException.class })
	ResponseEntity<ErrorResponse> handleUnavailable(RuntimeException exception) {
		return ResponseEntity.status(503)
			.body(new ErrorResponse("DEPENDENCY_UNAVAILABLE", "Dependencia temporariamente indisponivel.", Instant.now()));
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException exception) {
		return ResponseEntity.status(404)
			.body(new ErrorResponse("NOT_FOUND", "Recurso nao encontrado.", Instant.now()));
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ResponseEntity<ErrorResponse> handleMethodNotAllowed(Exception exception) {
		return ResponseEntity.status(405)
			.body(new ErrorResponse("METHOD_NOT_ALLOWED", "Metodo nao suportado para este recurso.", Instant.now()));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
		LOGGER.error("unhandled_exception", exception);
		return ResponseEntity.internalServerError()
			.body(new ErrorResponse("INTERNAL_ERROR", "Erro interno inesperado.", Instant.now()));
	}
}
