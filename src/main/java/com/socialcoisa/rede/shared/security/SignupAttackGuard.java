package com.socialcoisa.rede.shared.security;

/**
 * Portão de cadastro contra o padrão de ataque conhecido (as contas "probe" de 20/10 e 22/10).
 *
 * <p>Fica em {@code shared} pelo mesmo motivo do {@link IpBlockReader}: a identidade precisa
 * barrar o cadastro e a moderação é quem dona da lista de IPs. A implementação recusa o cadastro
 * E fecha o IP na hora — um ataque que nasce de um IP custa uma tentativa, não centenas.
 */
public interface SignupAttackGuard {

	/**
	 * Lança 403 SIGNUP_REFUSED quando o cadastro bate no padrão de ataque (e bloqueia o IP de
	 * origem). Cadastro normal não é tocado.
	 */
	void checkSignup(String name, String email, String ip);
}
