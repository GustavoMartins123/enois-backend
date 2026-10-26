package com.socialcoisa.rede.importlinkedin.adapters.in.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.socialcoisa.rede.importlinkedin.LinkedinImportService;
import com.socialcoisa.rede.shared.web.CurrentUser;
import com.socialcoisa.rede.shared.web.CurrentUserResolver;

/**
 * Importação do ARQUIVO DE DADOS do LinkedIn (Configurações → "Obter uma cópia dos seus
 * dados" → ZIP). Devolve o RETRATO parseado — nada é salvo automaticamente: a pessoa
 * revisa no front e salva pelas telas de perfil.
 */
@RestController
@RequestMapping("/me/import/linkedin")
class LinkedinImportController {

	private final LinkedinImportService service;
	private final CurrentUserResolver currentUserResolver;

	LinkedinImportController(LinkedinImportService service, CurrentUserResolver currentUserResolver) {
		this.service = service;
		this.currentUserResolver = currentUserResolver;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	LinkedinImportService.LinkedinImportPreview importArchive(
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestParam("file") MultipartFile file) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		return service.parse(file);
	}

	/**
	 * O caminho SEM ARQUIVO: a pessoa cola o texto do próprio perfil do LinkedIn (o formato
	 * que o navegador copia) e recebe o mesmo retrato da importação por ZIP.
	 */
	@PostMapping("/texto")
	LinkedinImportService.LinkedinImportPreview importText(
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestBody TextoRequest request) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		return service.parseTexto(request == null ? null : request.texto());
	}

	record TextoRequest(String texto) {
	}
}
