package com.socialcoisa.rede.media.adapters.out.storage;

import org.springframework.web.util.UriUtils;

import com.socialcoisa.rede.media.application.port.out.StorageUrlFactory;

import java.nio.charset.StandardCharsets;

class ImageKitStorageUrlFactory implements StorageUrlFactory {

	private final String urlEndpoint;

	ImageKitStorageUrlFactory(String urlEndpoint) {
		this.urlEndpoint = urlEndpoint.endsWith("/") ? urlEndpoint : urlEndpoint + "/";
	}

	@Override
	public com.socialcoisa.rede.media.domain.MediaStorageProvider provider() {
		return com.socialcoisa.rede.media.domain.MediaStorageProvider.IMAGEKIT;
	}

	@Override
	public String objectKey(String mediaId, String fileName) {
		// Segmento a segmento, como no S3: "." e ".." não são nomes e não podem virar a chave (E-41).
		String encoded = java.util.Arrays.stream(fileName.split("/", -1))
			.map(segment -> {
				String safe = segment.equals(".") || segment.equals("..") ? "_" : segment;
				return UriUtils.encodePathSegment(safe, StandardCharsets.UTF_8);
			})
			.collect(java.util.stream.Collectors.joining("/"));
		return "uploads/" + mediaId + "/" + encoded;
	}

	@Override
	public String cdnUrl(String objectKey) {
		return urlEndpoint + objectKey;
	}

	@Override
	public String uploadUrl(String objectKey) {
		return urlEndpoint + objectKey;
	}
}
