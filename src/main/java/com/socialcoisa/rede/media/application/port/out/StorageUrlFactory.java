package com.socialcoisa.rede.media.application.port.out;

public interface StorageUrlFactory {

	/** Provedor dono dos objetos que esta fabrica endereca (o upload novo nasce nele). */
	com.socialcoisa.rede.media.domain.MediaStorageProvider provider();

	String objectKey(String mediaId, String fileName);

	String cdnUrl(String objectKey);

	String uploadUrl(String objectKey);
}
