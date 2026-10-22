package com.socialcoisa.rede.media.application;

import java.time.Instant;
import java.util.Map;

import com.socialcoisa.rede.media.application.port.out.MediaUploadSigner;
import com.socialcoisa.rede.media.domain.MediaAsset;
import com.socialcoisa.rede.media.domain.MediaDeleteStatus;
import com.socialcoisa.rede.media.domain.MediaStatus;

/**
 * Resposta da criacao de upload.
 *
 * `provider` diz ao cliente COMO subir: com `S3`, `uploadUrl` e uma URL assinada e o arquivo vai
 * direto por `PUT` (repetindo os cabecalhos de `uploadHeaders`); com `IMAGEKIT`, o fluxo e o de
 * sempre (multipart assinado, que o front ja faz). E esse campo que permite os dois provedores
 * conviverem durante a transicao, sem o front adivinhar pelo formato da URL.
 */
public record MediaUploadView(String id, String ownerId, String fileName, String mimeType, long size, String purpose,
		String objectKey, String cdnUrl, String uploadUrl, String uploadMethod, Map<String, String> uploadHeaders,
		String provider, MediaStatus status, MediaDeleteStatus deleteStatus, Instant expiresAt) {

	private static final String IMAGEKIT = "IMAGEKIT";

	public static MediaUploadView from(MediaAsset asset, String uploadUrl) {
		return new MediaUploadView(asset.id(), asset.ownerId().value(), asset.fileName(), asset.mimeType(), asset.size(),
			asset.purpose(), asset.objectKey(), asset.cdnUrl(), uploadUrl, null, Map.of(), IMAGEKIT, asset.status(),
			asset.deleteStatus(), asset.expiresAt());
	}

	/** Com storage S3: URL assinada + metodo + cabecalhos que o cliente tem de repetir. */
	public static MediaUploadView fromSigned(MediaAsset asset, MediaUploadSigner.SignedUpload signed) {
		return new MediaUploadView(asset.id(), asset.ownerId().value(), asset.fileName(), asset.mimeType(), asset.size(),
			asset.purpose(), asset.objectKey(), asset.cdnUrl(), signed.url(), signed.method(), signed.headers(), "S3",
			asset.status(), asset.deleteStatus(), asset.expiresAt());
	}
}
