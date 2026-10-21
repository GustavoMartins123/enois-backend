package com.socialcoisa.rede.identity.application;

/** birthDate em ISO (yyyy-MM-dd); null recusa (ECA Digital: data de nascimento obrigatoria). */
public record SignupCommand(String name, String role, String email, String password, Boolean acceptedTerms,
		String birthDate) {
}
