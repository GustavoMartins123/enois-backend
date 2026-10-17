package com.socialcoisa.rede.feed.adapters.out.persistence;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.socialcoisa.rede.feed.adapters.out.persistence.SpringDataCommentJpaRepository;
import com.socialcoisa.rede.gifs.application.port.out.CommunityGifReader;

/**
 * Lê os GIFs que a comunidade já compartilhou: comentários que citam o termo e
 * carregam URL de GIF no próprio texto (a mesma que o reconhecimento de link do
 * front já renderiza). Dedupe por URL, mais recentes primeiro.
 */
@Component
class CommunityGifAdapter implements CommunityGifReader {

	private static final Pattern GIF_URL = Pattern.compile("https://[^\\s\"'<>]+?\\.gif", Pattern.CASE_INSENSITIVE);

	private final SpringDataCommentJpaRepository comments;

	CommunityGifAdapter(SpringDataCommentJpaRepository comments) {
		this.comments = comments;
	}

	@Override
	public List<String> search(String term, int limit) {
		Set<String> urls = new LinkedHashSet<>();
		for (String content : comments
			.searchGifContents(term.toLowerCase(), PageRequest.of(0, Math.max(limit * 4, 40)))) {
			Matcher matcher = GIF_URL.matcher(content);
			while (matcher.find()) {
				urls.add(matcher.group());
				if (urls.size() >= limit) {
					return List.copyOf(urls);
				}
			}
		}
		return List.copyOf(urls);
	}
}
