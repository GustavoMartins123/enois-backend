package com.socialcoisa.rede.media.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.socialcoisa.rede.media.application.port.out.MediaPublicUrlVerifier;
import com.socialcoisa.rede.media.application.port.out.MediaS3Copier;
import com.socialcoisa.rede.media.application.port.out.MediaSourceDownloader;
import com.socialcoisa.rede.media.application.port.out.MediaUrlMigrationRepository;
import com.socialcoisa.rede.media.application.port.out.MediaUrlReferenceUpdater;

/**
 * Migração oportunista das URLs legadas: host permitido (anti-SSRF), claim único, referências só
 * trocam com a URL pública respondendo, falha preserva tudo, caminho relativo vira URL absoluta.
 */
@ExtendWith(MockitoExtension.class)
class LazyUrlMigrationServiceTests {

	private static final String IK = "https://ik.imagekit.io/socialcoisa";
	private static final String ORIGINAL = IK + "/uploads/media_x/foto.png";
	private static final String CDN = "https://cdn.exemplo.com/uploads/media_x/foto.png";

	@Mock
	private MediaUrlMigrationRepository migrations;

	@Mock
	private MediaSourceDownloader downloader;

	@Mock
	private MediaS3Copier copier;

	@Mock
	private MediaPublicUrlVerifier verifier;

	@Mock
	private MediaUrlReferenceUpdater references;

	/** Executor direto: o "assíncrono" roda na hora do teste. */
	private final Executor direto = Runnable::run;

	private final Clock clock = Clock.fixed(Instant.parse("2026-10-19T18:00:00Z"), ZoneOffset.UTC);

	private LazyUrlMigrationService service() {
		return new LazyUrlMigrationService(migrations, downloader, copier, verifier, references, direto, clock, "");
	}

	@Test
	void ignoraHostsQueNaoSaoDoImageKit() {
		LazyUrlMigrationService service = service();

		service.onAccess("https://evil.example.com/foto.png");
		service.onAccess("http://ik.imagekit.io/socialcoisa/uploads/a/b.png"); // http, nao https
		service.onAccess("https://169.254.169.254/latest/meta-data/");

		verifyNoInteractions(migrations, downloader, copier, references);
	}

	@Test
	void aceitaCaminhoRelativoAntigo() {
		LazyUrlMigrationService service = service();
		assertThat(service.isLegacy("/uploads/media_y/foto.png")).isTrue();
		assertThat(service.absoluteSourceOf("/uploads/media_y/foto.png"))
			.isEqualTo("https://ik.imagekit.io/socialcoisa/uploads/media_y/foto.png");
	}

	@Test
	void agendaCopiaEAtivaQuandoAUrlPublicaResponde() throws Exception {
		Path tmp = Files.createTempFile("lazy-", ".png");
		Files.writeString(tmp, "png");
		when(downloader.download(ORIGINAL)).thenReturn(tmp);
		when(copier.copy("uploads/media_x/foto.png", tmp, "image/png")).thenReturn(CDN);
		when(migrations.claim(anyString(), any(), any(), anyInt())).thenReturn(true);
		when(verifier.verify(CDN, "image/png")).thenReturn(true);

		service().onAccess(ORIGINAL);

		verify(migrations).ensurePending(anyString(), eq(ORIGINAL), any());
		verify(migrations).markCopied(anyString(), eq(CDN), any());
		verify(migrations).markActive(anyString(), any());
		verify(references).updateReferences(ORIGINAL, CDN);
	}

	@Test
	void copiadaMasSemVerificacaoPublicaNaoTrocaReferencias() throws Exception {
		Path tmp = Files.createTempFile("lazy-", ".png");
		Files.writeString(tmp, "png");
		when(downloader.download(ORIGINAL)).thenReturn(tmp);
		when(copier.copy(anyString(), any(), anyString())).thenReturn(CDN);
		when(migrations.claim(anyString(), any(), any(), anyInt())).thenReturn(true);
		when(verifier.verify(CDN, "image/png")).thenReturn(false);

		service().onAccess(ORIGINAL);

		verify(migrations).markCopied(anyString(), eq(CDN), any());
		verify(migrations, never()).markActive(anyString(), any());
		verify(references, never()).updateReferences(anyString(), anyString());
	}

	@Test
	void falhaNoDownloadRegistraErroEPreservaReferencia() {
		when(migrations.claim(anyString(), any(), any(), anyInt())).thenReturn(true);
		when(downloader.download(ORIGINAL)).thenThrow(new IllegalStateException("origem respondeu 404"));

		service().onAccess(ORIGINAL);

		verify(migrations).markFailed(anyString(), eq("origem respondeu 404"), any());
		verify(references, never()).updateReferences(anyString(), anyString());
	}

	@Test
	void semClaimNaoBaixaNemCopia() {
		when(migrations.claim(anyString(), any(), any(), anyInt())).thenReturn(false);

		service().onAccess(ORIGINAL);

		verify(downloader, never()).download(anyString());
		verify(references, never()).updateReferences(anyString(), anyString());
	}
}
