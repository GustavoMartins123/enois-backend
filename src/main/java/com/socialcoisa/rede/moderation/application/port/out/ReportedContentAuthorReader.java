package com.socialcoisa.rede.moderation.application.port.out;

import java.util.Optional;

/**
 * Autor do conteudo denunciado (quem sofre a sancao). Implementado pelo modulo de feed; outros
 * alvos (perfil, vaga) ficam para revisao humana — a sancao automatica so vale para post e
 * comentario, onde o autor e inequivoco.
 */
public interface ReportedContentAuthorReader {

	Optional<String> authorOf(String targetType, String targetId);
}
