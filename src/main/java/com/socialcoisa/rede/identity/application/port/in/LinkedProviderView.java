package com.socialcoisa.rede.identity.application.port.in;

/**
 * Um provedor vinculado a conta. `hasUsablePassword` diz se existe outra forma de entrar — e o que a
 * tela usa para avisar antes de desvincular.
 */
public record LinkedProviderView(String provider, String email, String linkedAt, boolean hasUsablePassword) {
}
