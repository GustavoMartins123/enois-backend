package com.socialcoisa.rede.identity.adapters.in.web;

public record SignupRequest(String name, String role, String email, String password, Boolean acceptedTerms,
		String turnstileToken, String website, String birthDate) {
}
