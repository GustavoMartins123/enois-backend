package com.socialcoisa.rede.media.adapters.out.storage;

import java.nio.charset.StandardCharsets;

import org.springframework.web.util.UriUtils;

import com.socialcoisa.rede.media.application.port.out.StorageUrlFactory;

/**
 * Endereços dos objetos no S3 — o storage novo da plataforma.
 *
 * Só compõe URL (nenhuma chamada de rede aqui): a leitura de mídia continua sendo "o endereço que
 * está gravado no asset", então o que já existe no ImageKit segue abrindo pelo ImageKit e o que
 * nasce agora aponta para o S3 — a convivência não precisa de caminho especial no código.
 *
 * O endereço público tem duas formas: com CDN na frente (`REDE_MEDIA_S3_PUBLIC_BASE_URL`, o caso
 * bom) ou direto no bucket (`https://{bucket}.s3.{region}.amazonaws.com`). Nos dois casos o valor
 * é estável e não expira — é isso que permite guardar o endereço no banco e usá-lo no feed e na
 * miniatura (URL assinada apodreceria).
 *
 * Credencial não aparece aqui de propósito: quem assina/sobe é o SDK, resolvendo a credencial
 * dentro do pod (cadeia padrão: IRSA/env do container).
 */
class S3StorageUrlFactory implements StorageUrlFactory {

	private final String bucket;
	private final String region;
	private final String prefix;
	private final String publicBaseUrl;

	S3StorageUrlFactory(String bucket, String region, String prefix, String publicBaseUrl) {
		if (bucket == null || bucket.isBlank()) {
			throw new IllegalStateException("rede.media.s3.bucket e obrigatorio quando o storage e s3");
		}
		this.bucket = bucket.trim();
		this.region = region == null || region.isBlank() ? "us-east-1" : region.trim();
		this.prefix = normalizePrefix(prefix);
		this.publicBaseUrl = publicBaseUrl == null || publicBaseUrl.isBlank() ? null : publicBaseUrl.trim();
	}

	@Override
	public com.socialcoisa.rede.media.domain.MediaStorageProvider provider() {
		return com.socialcoisa.rede.media.domain.MediaStorageProvider.S3;
	}

	@Override
	public String objectKey(String mediaId, String fileName) {
		// Mesma forma do ImageKit (`uploads/{id}/{arquivo}`): quem migrar depois sabe onde procurar.
		// As BARRAS do nome são separadores de verdade (o front manda `media/<uuid>/<arquivo>`):
		// codificar o nome inteiro viraria `media%2F<uuid>%2F...` dentro da CHAVE, e a URL pública
		// (que decodifica) passaria a apontar para outra chave. Codifica segmento a segmento.
		String encoded = java.util.Arrays.stream(fileName.split("/", -1))
			.map(segment -> {
				// "." e ".." não são nomes: CDNs costumam normalizar o caminho e mudariam a chave.
				String safe = segment.equals(".") || segment.equals("..") ? "_" : segment;
				return UriUtils.encodePathSegment(safe, StandardCharsets.UTF_8);
			})
			.collect(java.util.stream.Collectors.joining("/"));
		return prefix + mediaId + "/" + encoded;
	}

	@Override
	public String cdnUrl(String objectKey) {
		return baseUrl() + objectKey;
	}

	/** Endereço do objeto em si: com S3 o upload direto é por URL assinada, gerada à parte. */
	@Override
	public String uploadUrl(String objectKey) {
		return cdnUrl(objectKey);
	}

	/** CDN quando configurada; senão o endereço do bucket na região. */
	String baseUrl() {
		if (publicBaseUrl != null) {
			return publicBaseUrl.endsWith("/") ? publicBaseUrl : publicBaseUrl + "/";
		}
		return "https://" + bucket + ".s3." + region + ".amazonaws.com/";
	}

	/**
	 * Sem prefixo definido, `uploads/` (mesma forma dos objetos do ImageKit). Prefixo so com barras
	 * significa a RAIZ do bucket — é o que a pessoa pediu ao escrever `/`; tratá-lo como "vazio"
	 * seria adivinhar em cima de uma escolha explícita.
	 */
	private static String normalizePrefix(String prefix) {
		if (prefix == null || prefix.isBlank()) {
			return "uploads/";
		}
		String trimmed = prefix;
		while (trimmed.startsWith("/")) {
			trimmed = trimmed.substring(1);
		}
		if (trimmed.isEmpty()) {
			return "";
		}
		return trimmed.endsWith("/") ? trimmed : trimmed + "/";
	}
}
