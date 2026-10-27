package com.socialcoisa.rede.profile.application;

import com.socialcoisa.rede.profile.domain.UserProfile;

/**
 * Personalizacao visual do nome (tema, fonte e cor) escolhida no perfil. Viaja junto com o nome em
 * toda projecao que o exibe (post, comentario, notificacao, ranking, equipe...), sempre como nulo
 * quando a pessoa nao personalizou — o front aplica o padrao do design system nesse caso.
 */
public record NameStyleView(String theme, String font, String color) {

	public static NameStyleView from(UserProfile profile) {
		if (profile == null) {
			return null;
		}
		String theme = profile.nameTheme();
		String font = profile.nameFont();
		String color = profile.nameColor();
		if (theme == null && font == null && color == null) {
			return null;
		}
		return new NameStyleView(theme, font, color);
	}
}