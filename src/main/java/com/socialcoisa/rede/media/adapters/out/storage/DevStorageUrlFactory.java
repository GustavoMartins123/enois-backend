package com.socialcoisa.rede.media.adapters.out.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import com.socialcoisa.rede.media.application.port.out.StorageUrlFactory;

@Component
@ConditionalOnMissingBean(StorageUrlFactory.class)
class DevStorageUrlFactory implements StorageUrlFactory {

	@Override
	public com.socialcoisa.rede.media.domain.MediaStorageProvider provider() {
		return com.socialcoisa.rede.media.domain.MediaStorageProvider.IMAGEKIT;
	}

	@Override
	public String objectKey(String mediaId, String fileName) {
		String safe = fileName.replaceAll("(^|/)\\.{1,2}(/|$)", "$1_$2");
		return "dev/" + mediaId + "/" + UriUtils.encodePathSegment(safe, java.nio.charset.StandardCharsets.UTF_8);
	}

	@Override
	public String cdnUrl(String objectKey) {
		return "/dev/cdn/" + objectKey;
	}

	@Override
	public String uploadUrl(String objectKey) {
		return "/dev/uploads/" + objectKey;
	}
}
