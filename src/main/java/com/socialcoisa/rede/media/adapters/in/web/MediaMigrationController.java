package com.socialcoisa.rede.media.adapters.in.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.socialcoisa.rede.media.application.port.out.MediaAssetRepository;
import com.socialcoisa.rede.media.domain.MediaStorageProvider;

/**
 * Progresso da migracao (contagens agregadas; nenhum id, URL ou dono). Publico de proposito: e o
 * mesmo tipo de numero do contador de comunidade — nao expoe midia nem pessoa.
 */
@RestController
@RequestMapping("/media/migration")
class MediaMigrationController {

	private final MediaAssetRepository repository;
	private final boolean enabled;
	private final boolean flipEnabled;

	MediaMigrationController(MediaAssetRepository repository,
			@Value("${rede.media.migration.enabled:false}") boolean enabled,
			@Value("${rede.media.migration.flip-enabled:false}") boolean flipEnabled) {
		this.repository = repository;
		this.enabled = enabled;
		this.flipEnabled = flipEnabled;
	}

	@GetMapping
	Map<String, Object> status() {
		Map<String, Object> view = new LinkedHashMap<>();
		view.put("enabled", enabled);
		view.put("flipEnabled", flipEnabled);
		view.put("total", repository.countAll());
		view.put("imagekit", repository.countByStorageProvider(MediaStorageProvider.IMAGEKIT));
		view.put("s3Copied", repository.countMigratedToS3());
		view.put("s3Active", repository.countActiveOnS3());
		view.put("errors", repository.countWithMigrationError());
		// Diagnostico: sem logs acessiveis, o ultimo erro (ex.: AccessDenied) e o que diz se o pod
		// tem permissao no bucket. Nao carrega URL nem id.
		view.put("lastError", repository.lastMigrationError().orElse(null));
		return view;
	}
}
