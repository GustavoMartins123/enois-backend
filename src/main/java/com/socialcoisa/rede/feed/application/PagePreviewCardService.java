package com.socialcoisa.rede.feed.application;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import javax.imageio.ImageIO;

import org.jsoup.Jsoup;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Cartão de compartilhamento de uma PÁGINA: a imagem que aparece na miniatura do link.
 *
 * Por que gerar aqui, e não usar uma imagem de marca: quem publica uma página quer ver o que
 * ESCREVEU na miniatura, não o logo do app. E página é HTML — não existe arquivo de imagem para
 * apontar, e tirar screenshot exigiria um navegador no servidor.
 *
 * Então o cartão é desenhado a partir do TEXTO da página (jsoup tira markup e CSS): as primeiras
 * linhas viram o título do cartão, o resto vira uma linha de apoio, com o autor e a assinatura do
 * énóis. É determinístico, não depende de serviço externo e cabe no cache.
 */
@Service
public class PagePreviewCardService {

	static final int WIDTH = 1200;
	static final int HEIGHT = 630;

	private static final Color BRAND_DARK = new Color(0x2A0F52);
	private static final Color BRAND_LIGHT = new Color(0x7C3AED);
	private static final Color CARD_TINT = new Color(255, 255, 255, 26);
	private static final Color BODY_TINT = new Color(0xE9E4FF);
	private static final Color FOOTER_TINT = new Color(0xC9BCF5);

	private static final int TITLE_CHARS = 120;
	private static final int BODY_CHARS = 180;

	/** Fonte empacotada: sem ela, container enxuto desenha cartão vazio. */
	private static final Font BOLD = loadBundledFont();

	/**
	 * Cartão pronto em PNG. O cache evita redesenhar a cada vez que a plataforma busca a imagem —
	 * o mesmo motivo do contador da comunidade: é dado recalculável, não precisa ser exato ao segundo.
	 */
	@Cacheable(value = "pagePreviewCards", key = "#postId")
	public byte[] render(String postId, String html, String authorName) {
		String text = Jsoup.parse(html == null ? "" : html).text().trim();
		String title = excerpt(text, TITLE_CHARS);
		String body = text.length() > TITLE_CHARS ? excerpt(text.substring(TITLE_CHARS), BODY_CHARS) : "";

		BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
				RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

			g.setPaint(new GradientPaint(0, 0, BRAND_DARK, WIDTH, HEIGHT, BRAND_LIGHT));
			g.fillRect(0, 0, WIDTH, HEIGHT);
			g.setColor(CARD_TINT);
			g.fillRoundRect(56, 56, WIDTH - 112, HEIGHT - 112, 40, 40);

			g.setFont(BOLD.deriveFont(58f));
			g.setColor(Color.WHITE);
			int y = drawWrapped(g, title.isEmpty() ? "Página no Énóis" : title, 112, 200, WIDTH - 224, 3, 74);

			if (!body.isEmpty()) {
				g.setFont(BOLD.deriveFont(34f));
				g.setColor(BODY_TINT);
				drawWrapped(g, body, 112, y + 40, WIDTH - 224, 2, 46);
			}

			g.setFont(BOLD.deriveFont(28f));
			g.setColor(FOOTER_TINT);
			g.drawString("Página de " + (authorName == null || authorName.isBlank() ? "alguém" : authorName),
				112, HEIGHT - 92);
			g.drawString("Énóis", WIDTH - 200, HEIGHT - 92);
		}
		finally {
			g.dispose();
		}

		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			ImageIO.write(image, "png", out);
			return out.toByteArray();
		}
		catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	/** Escreve o texto quebrando em palavras, e devolve o Y da última linha. */
	private int drawWrapped(Graphics2D g, String text, int x, int y, int maxWidth, int maxLines, int lineHeight) {
		java.util.List<String> lines = new java.util.ArrayList<>();
		StringBuilder current = new StringBuilder();
		for (String word : text.split("\\s+")) {
			String candidate = current.isEmpty() ? word : current + " " + word;
			if (g.getFontMetrics().stringWidth(candidate) > maxWidth && !current.isEmpty()) {
				lines.add(current.toString());
				current = new StringBuilder(word);
				if (lines.size() == maxLines) break;
			}
			else {
				current = new StringBuilder(candidate);
			}
		}
		if (lines.size() < maxLines && !current.isEmpty()) {
			lines.add(current.toString());
		}

		int cursor = y;
		for (int index = 0; index < lines.size(); index++) {
			String line = lines.get(index);
			boolean last = index == lines.size() - 1;
			g.drawString(last && text.split("\\s+").length > countWords(lines) ? line + "…" : line, x, cursor);
			cursor += lineHeight;
		}
		return cursor - lineHeight;
	}

	private int countWords(java.util.List<String> lines) {
		int total = 0;
		for (String line : lines) {
			total += line.split("\\s+").length;
		}
		return total;
	}

	/** Corte em palavra inteira: cartão com palavra picada lê pior. */
	private static String excerpt(String text, int limit) {
		String collapsed = text.replaceAll("\\s+", " ").trim();
		if (collapsed.length() <= limit) return collapsed;
		String cut = collapsed.substring(0, limit);
		int lastSpace = cut.lastIndexOf(' ');
		return (lastSpace > limit / 2 ? cut.substring(0, lastSpace) : cut).trim();
	}

	private static Font loadBundledFont() {
		try (InputStream stream = PagePreviewCardService.class.getResourceAsStream("/fonts/LiberationSans-Bold.ttf")) {
			if (stream != null) {
				return Font.createFont(Font.TRUETYPE_FONT, stream);
			}
		}
		catch (FontFormatException | IOException ignored) {
			// Cai na fonte logica: melhor um cartao com fonte do sistema do que nenhum cartao.
		}
		return new Font(Font.SANS_SERIF, Font.BOLD, 48);
	}
}
