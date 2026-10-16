package com.socialcoisa.rede.shared.application;

import java.time.Duration;
import java.util.List;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Cache em memoria do processo.
 *
 * Serve para contadores e agregados que custam uma varredura no banco e nao precisam ser exatos ao
 * segundo — hoje o contador de usuarios ativos da landing. E de proposito em memoria (nao Redis):
 * o dado e recalculavel, cada pod pode ter o seu proprio numero por alguns minutos, e assim nao
 * entra dependencia de infra nova por causa de um contador.
 *
 * Lista de caches fechada: cache sem TTL e sem dono conhecido virou fonte de bug em todo lugar que
 * eu vi; aqui cada um precisa ser declarado com o seu frescor.
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
public class CacheConfig {

	/** Frescor do contador da comunidade: o numero pode ficar ate 10 min atras do banco. */
	private static final Duration COMMUNITY_STATS_TTL = Duration.ofMinutes(10);

	@Bean
	CacheManager cacheManager() {
		SimpleCacheManager manager = new SimpleCacheManager();
		manager.setCaches(List.of(cache("communityStats", COMMUNITY_STATS_TTL)));
		return manager;
	}

	private CaffeineCache cache(String name, Duration ttl) {
		return new CaffeineCache(name,
			Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(16).build());
	}
}
