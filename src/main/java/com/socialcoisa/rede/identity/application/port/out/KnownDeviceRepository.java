package com.socialcoisa.rede.identity.application.port.out;

public interface KnownDeviceRepository {

	boolean exists(String userId, String fingerprint);

	void save(String userId, String fingerprint);
}
