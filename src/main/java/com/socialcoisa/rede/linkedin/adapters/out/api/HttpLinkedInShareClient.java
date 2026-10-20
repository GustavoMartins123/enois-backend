package com.socialcoisa.rede.linkedin.adapters.out.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.linkedin.application.port.out.LinkedInShareClient;
import com.socialcoisa.rede.linkedin.application.port.out.LinkedInShareClient.ShareOutcome;

import tools.jackson.databind.ObjectMapper;

@Component
class HttpLinkedInShareClient implements LinkedInShareClient {

	private static final Logger LOG = LoggerFactory.getLogger(HttpLinkedInShareClient.class);
	private static final String UGC_POSTS_URL = "https://api.linkedin.com/v2/ugcPosts";

	private final ObjectMapper objectMapper;
	private final HttpClient http;

	HttpLinkedInShareClient(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
		this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	}

	@Override
	public ShareOutcome share(
			String accessToken, String memberSub, String commentary, String url, String title) {
		if (!StringUtils.hasText(accessToken) || !StringUtils.hasText(memberSub)) {
			return ShareOutcome.failed("SEM_TOKEN");
		}
		try {
			Map<String, Object> payload = Map.of(
				"author", "urn:li:person:" + memberSub,
				"lifecycleState", "PUBLISHED",
				"specificContent", Map.of("com.linkedin.ugc.ShareContent", Map.of(
					"shareCommentary", Map.of("text", commentary),
					"shareMediaCategory", "ARTICLE",
					"media", List.of(Map.of(
						"status", "READY",
						"originalUrl", url,
						"title", Map.of("text", title))))),
				"visibility", Map.of("com.linkedin.ugc.MemberNetworkVisibility", "PUBLIC"));

			HttpRequest request = HttpRequest.newBuilder(URI.create(UGC_POSTS_URL))
				.header("Authorization", "Bearer " + accessToken)
				.header("Content-Type", "application/json")
				.header("X-Restli-Protocol-Version", "2.0.0")
				.timeout(Duration.ofSeconds(15))
				.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
				.build();

			HttpResponse<String> response = http.send(request, BodyHandlers.ofString());
			if (response.statusCode() == 201) {
				return ShareOutcome.ok();
			}
			// 401/403 = token sem w_member_social ou expirado; 422 = texto/URL recusados.
			LOG.warn("LinkedIn recusou a publicacao: status={} body={}", response.statusCode(),
				response.body() == null ? "" : response.body().substring(0, Math.min(300, response.body().length())));
			return ShareOutcome.failed("HTTP_" + response.statusCode());
		}
		catch (Exception exception) {
			LOG.warn("Falha ao publicar no LinkedIn: {}", exception.getMessage());
			return ShareOutcome.failed("EXCEPTION");
		}
	}
}
