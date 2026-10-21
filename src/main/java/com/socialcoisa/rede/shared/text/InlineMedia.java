package com.socialcoisa.rede.shared.text;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Imagem/mídia NO MEIO do texto (item 9 do backlog, pedido da comunidade de games): o conteúdo
 * pode conter marcadores {@code {{media:<id>}}} e quem renderiza troca cada um pela mídia real,
 * exatamente no parágrafo onde o autor colocou. Sem marcador, a mídia continua sendo anexo no
 * fim — o comportamento de sempre.
 *
 * <p>A regra de segurança é simples: todo marcador precisa referenciar uma mídia da MESMA
 * publicação (a lista {@code mediaIds}, que o serviço já valida como sendo do autor). Um
 * marcador órfão apontando para mídia alheia ou inexistente não passa — senão qualquer post
 * exibiria mídia de outros como se fosse sua.</p>
 */
public final class InlineMedia {

	public static final Pattern MARKER = Pattern.compile("\\{\\{media:([A-Za-z0-9_-]+)}}");

	private InlineMedia() {
	}

	/** Ids referenciados por marcadores no texto, na ordem em que aparecem, sem repetição. */
	public static List<String> idsIn(String content) {
		if (content == null) {
			return List.of();
		}
		Set<String> seen = new HashSet<>();
		List<String> ids = new ArrayList<>();
		Matcher matcher = MARKER.matcher(content);
		while (matcher.find()) {
			String id = matcher.group(1);
			if (seen.add(id)) {
				ids.add(id);
			}
		}
		return ids;
	}

	/**
	 * Todo marcador do texto precisa estar na lista de mídias da publicação. A validação morre
	 * em erro de requisição, não em silêncio: marcador solto é erro do autor, e mostrar mídia
	 * alheia disfarçada de anexo seria pior que recusar.
	 */
	public static void validate(String content, List<String> mediaIds) {
		List<String> referenced = idsIn(content);
		if (referenced.isEmpty()) {
			return;
		}
		Set<String> owned = mediaIds == null ? Set.of() : new HashSet<>(mediaIds);
		for (String id : referenced) {
			if (!owned.contains(id)) {
				throw new ApiException(400, "INVALID_INLINE_MEDIA",
					"Há uma mídia inserida no texto que não está nos anexos da publicação. "
						+ "Anexe a mídia e insira ela no texto.");
			}
		}
	}
}
