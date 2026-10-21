package com.socialcoisa.rede.shared.age;

import org.springframework.stereotype.Component;

import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Porta de entrada do enforcement da política de idade (ECA Digital). Os serviços de escrita
 * chamam antes de executar a ação; a mensagem e o código são únicos para o front abrir a tela
 * de regularização em qualquer ponto do app.
 */
@Component
public class AgeGate {

	public static final String CODE = "BIRTH_DATE_REQUIRED";

	private final AgePolicyReader reader;

	public AgeGate(AgePolicyReader reader) {
		this.reader = reader;
	}

	/** Ação exige data de nascimento declarada (regularização da base). */
	public void requireDeclared(String userId) {
		if (!reader.birthDateDeclared(userId)) {
			throw new ApiException(403, CODE,
				"Confirme sua data de nascimento para continuar. É um requisito da plataforma.");
		}
	}
}
