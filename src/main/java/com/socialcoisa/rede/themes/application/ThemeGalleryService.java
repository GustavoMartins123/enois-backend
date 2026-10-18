package com.socialcoisa.rede.themes.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.shared.web.ApiException;
import com.socialcoisa.rede.themes.application.port.in.ThemeGalleryUseCase;
import com.socialcoisa.rede.themes.application.port.out.ThemeGalleryRepository;
import com.socialcoisa.rede.themes.domain.ThemeGalleryStatus;
import com.socialcoisa.rede.themes.domain.ThemePackage;

/**
 * Galeria de temas da comunidade (Fase 2). O pacote publicado e JSON de TOKENS — a validacao
 * e estrita por schema (hex, enums, teto de tamanho); CSS livre, url() e imagens sao
 * rejeitados na porta. A cor de texto de cada modo e conferida contra o fundo pelo mesmo
 * calculo do auditor de contraste (>= 4.5:1) — tema ilegivel nao publica.
 */
@Service
public class ThemeGalleryService implements ThemeGalleryUseCase {

	private static final int MAX_PACKAGE_CHARS = 4096;
	private static final Set<String> MODES = Set.of("light", "dark");
	private static final String HEX = "#[0-9a-fA-F]{6}";

	private final ThemeGalleryRepository themes;
	private final Clock clock;

	public ThemeGalleryService(ThemeGalleryRepository themes, Clock clock) {
		this.themes = themes;
		this.clock = clock;
	}

	/** Formas de cor permitidas: hex de 6 digitos, ou color-mix dos dois hex do pacote. */
	private static final java.util.regex.Pattern HEX_OK = java.util.regex.Pattern.compile("^" + HEX + "$");
	private static final java.util.regex.Pattern GRADIENT_OK = java.util.regex.Pattern
		.compile("^linear-gradient\\(\\d+deg, " + HEX + ", " + HEX + "\\)$");

	@Override
	@Transactional
	public ThemePackage publish(String userId, String name, String description, String packageJson, String modeDefault) {
		if (!StringUtils.hasText(name) || name.trim().length() < 3 || name.trim().length() > 80) {
			throw new ApiException(400, "INVALID_THEME_NAME", "Nome do tema precisa de 3 a 80 caracteres.");
		}
		if (!StringUtils.hasText(packageJson) || packageJson.length() > MAX_PACKAGE_CHARS) {
			throw new ApiException(400, "INVALID_THEME_PACKAGE",
				"Pacote do tema invalido ou grande demais (teto de " + MAX_PACKAGE_CHARS + " caracteres).");
		}
		if (!MODES.contains(modeDefault)) {
			throw new ApiException(400, "INVALID_THEME_MODE", "Modo padrao deve ser light ou dark.");
		}
		validatePackageJson(packageJson);
		ThemePackage saved = themes.save(ThemePackage.create(
			"theme_" + UUID.randomUUID().toString().replace("-", ""), userId, name.trim(),
			StringUtils.hasText(description) ? description.trim() : null, packageJson, modeDefault,
			clock.instant()));
		return saved;
	}

	@Override
	@Transactional(readOnly = true)
	public List<ThemePackage> list(int limit) {
		return themes.findPublished(limit);
	}

	@Override
	@Transactional(readOnly = true)
	public List<ThemePackage> mine(String userId) {
		return themes.findByAuthor(userId, 50);
	}

	@Override
	@Transactional(readOnly = true)
	public ThemePackage get(String id) {
		return themes.findById(id).filter(t -> t.status() == ThemeGalleryStatus.PUBLISHED)
			.orElseThrow(() -> new ApiException(404, "THEME_NOT_FOUND", "Tema nao encontrado na galeria."));
	}

	@Override
	@Transactional
	public void report(String themeId) {
		ThemePackage theme = themes.findById(themeId)
			.orElseThrow(() -> new ApiException(404, "THEME_NOT_FOUND", "Tema nao encontrado."));
		themes.save(theme.report());
	}

	@Override
	@Transactional
	public void install(String themeId) {
		ThemePackage theme = themes.findById(themeId)
			.filter(t -> t.status() == ThemeGalleryStatus.PUBLISHED)
			.orElseThrow(() -> new ApiException(404, "THEME_NOT_FOUND", "Tema nao encontrado."));
		themes.save(theme.addInstall());
	}

	@Override
	@Transactional
	public void hide(String themeId, String userId) {
		ThemePackage theme = themes.findById(themeId)
			.orElseThrow(() -> new ApiException(404, "THEME_NOT_FOUND", "Tema nao encontrado."));
		if (!theme.isOwnedBy(userId)) {
			throw new ApiException(403, "NOT_AUTHORIZED", "So o autor pode remover o tema da galeria.");
		}
		themes.save(theme.hide());
	}

	/**
	 * Validacao do pacote: o JSON tem exatamente as chaves do tema, cores em hex de 6
	 * digitos, gradiente na forma permitida, e o contraste texto-fundo de cada modo
	 * >= 4.5:1 (o mesmo calculo do auditor de contraste, aqui na porta).
	 */
	private void validatePackageJson(String packageJson) {
		Map<?, ?> pkg;
		try {
			pkg = new tools.jackson.databind.ObjectMapper().readValue(packageJson, Map.class);
		}
		catch (Exception invalid) {
			throw new ApiException(400, "INVALID_THEME_PACKAGE", "Pacote nao e um JSON valido.");
		}
		Object paletteObj = pkg.get("palette");
		if (!(paletteObj instanceof Map)) {
			throw new ApiException(400, "INVALID_THEME_PACKAGE", "Pacote precisa do objeto palette.");
		}
		Map<?, ?> palette = (Map<?, ?>) paletteObj;
		checkColor(palette.get("background"), "background");
		checkColor(palette.get("surface"), "surface");
		checkColor(palette.get("border"), "border");
		checkColor(palette.get("primary"), "primary");
		Object gradient = palette.get("gradient");
		if (gradient != null && !GRADIENT_OK.matcher(String.valueOf(gradient)).matches()) {
			throw new ApiException(400, "INVALID_THEME_PACKAGE", "Gradiente fora do formato permitido.");
		}
		Object text = palette.get("text");
		if (!(text instanceof Map)) {
			throw new ApiException(400, "INVALID_THEME_PACKAGE", "Pacote precisa do objeto text.");
		}
		Map<?, ?> textMap = (Map<?, ?>) text;
		checkColor(textMap.get("strong"), "text.strong");
		checkColor(textMap.get("subtle"), "text.subtle");
		// O mesmo calculo do auditor: contraste do texto forte sobre o fundo, por modo.
		double ratio = contrastRatio(String.valueOf(textMap.get("strong")),
			String.valueOf(palette.get("background")));
		if (ratio < 4.5) {
			throw new ApiException(400, "THEME_CONTRAST_LOW",
				"O contraste do texto com o fundo precisa ser de pelo menos 4.5:1.");
		}
	}

	private void checkColor(Object value, String field) {
		if (value == null || !HEX_OK.matcher(String.valueOf(value)).matches()) {
			throw new ApiException(400, "INVALID_THEME_PACKAGE", "Cor invalida em " + field + ".");
		}
	}

	private double contrastRatio(String fg, String bg) {
		try {
			double[] f = rgb(fg), b = rgb(bg);
			return (Math.max(lum(f), lum(b)) + 0.05) / (Math.min(lum(f), lum(b)) + 0.05);
		}
		catch (Exception invalid) {
			return 0;
		}
	}

	private double lum(double[] c) {
		java.util.function.DoubleUnaryOperator f = (v) -> {
			v /= 255;
			return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
		};
		return 0.2126 * f.applyAsDouble(c[0]) + 0.7152 * f.applyAsDouble(c[1]) + 0.0722 * f.applyAsDouble(c[2]);
	}

	private double[] rgb(String hex) {
		String h = hex.replace("#", "");
		return new double[] { Integer.parseInt(h.substring(0, 2), 16), Integer.parseInt(h.substring(2, 4), 16),
			Integer.parseInt(h.substring(4, 6), 16) };
	}
}
