package com.socialcoisa.rede.gifs.adapters.out.providers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.socialcoisa.rede.gifs.application.GifItem;

import tools.jackson.databind.ObjectMapper;

/**
 * Leitura do JSON de cada provedor, sem rede: o formato e de terceiro e muda sem avisar, entao o
 * mapeamento tem teste proprio (URL ausente nao entra na grade, previa ausente cai para a midia
 * cheia, dimensao em texto do GIPHY vira numero).
 */
@SpringBootTest
class GifProvidersMappingTests {

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void mapsTenorResults() {
		String json = """
			{"results":[
			  {"id":"1","content_description":"gato dançando",
			   "media_formats":{
			     "gif":{"url":"https://media.tenor.com/1/gato.gif","dims":[498,498]},
			     "tinygif":{"url":"https://media.tenor.com/1/gato-tiny.gif","dims":[220,220]}}},
			  {"id":"2","content_description":"sem gif",
			   "media_formats":{"tinygif":{"url":"https://media.tenor.com/2/so-tiny.gif","dims":[220,220]}}},
			  {"id":"3","content_description":"vazio","media_formats":{}}
			]}""";

		var items = TenorGifProvider.map(objectMapper.readTree(json));

		assertThat(items).hasSize(2);
		GifItem first = items.get(0);
		assertThat(first.url()).isEqualTo("https://media.tenor.com/1/gato.gif");
		assertThat(first.previewUrl()).isEqualTo("https://media.tenor.com/1/gato-tiny.gif");
		assertThat(first.width()).isEqualTo(498);
		assertThat(first.description()).isEqualTo("gato dançando");
		assertThat(first.provider()).isEqualTo("Tenor");
		// Sem `gif`, a previa vira a midia principal — melhor um GIF pequeno do que nenhum.
		assertThat(items.get(1).url()).isEqualTo("https://media.tenor.com/2/so-tiny.gif");
	}

	@Test
	void mapsGiphyResults() {
		String json = """
			{"data":[
			  {"id":"a","title":"cachorro",
			   "images":{
			     "original":{"url":"https://media.giphy.com/media/a/giphy.gif","width":"480","height":"270"},
			     "fixed_width":{"url":"https://media.giphy.com/media/a/200w.gif","width":"200","height":"113"}}},
			  {"id":"b","title":"sem original","images":{"fixed_width":{"url":"https://media.giphy.com/media/b/200w.gif"}}}
			]}""";

		var items = GiphyGifProvider.map(objectMapper.readTree(json));

		assertThat(items).hasSize(1);
		GifItem first = items.get(0);
		assertThat(first.url()).isEqualTo("https://media.giphy.com/media/a/giphy.gif");
		assertThat(first.previewUrl()).isEqualTo("https://media.giphy.com/media/a/200w.gif");
		assertThat(first.width()).isEqualTo(480);
		assertThat(first.height()).isEqualTo(270);
		assertThat(first.provider()).isEqualTo("GIPHY");
	}

	@Test
	void emptyPayloadMeansEmptyList() {
		assertThat(TenorGifProvider.map(objectMapper.readTree("{\"results\":[]}"))).isEmpty();
		assertThat(GiphyGifProvider.map(objectMapper.readTree("{\"data\":[]}"))).isEmpty();
	}
}
