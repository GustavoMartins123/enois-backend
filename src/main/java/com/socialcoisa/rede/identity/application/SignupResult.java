package com.socialcoisa.rede.identity.application;

/**
 * Resultado do cadastro. A conta nasce pendente: nenhuma sessao e emitida antes da confirmacao do
 * e-mail, entao o cadastro devolve apenas para onde o link foi enviado.
 *
 * @param email            e-mail (normalizado) que recebeu o link
 * @param verificationRequired sempre true hoje; explicito para o cliente nao precisar adivinhar
 * @param emailSent        false quando o envio falhou — a tela oferece reenviar
 */
public record SignupResult(String email, boolean verificationRequired, boolean emailSent) {
}