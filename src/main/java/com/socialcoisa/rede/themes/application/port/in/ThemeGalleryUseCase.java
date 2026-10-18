package com.socialcoisa.rede.themes.application.port.in;

import java.util.List;

import com.socialcoisa.rede.themes.domain.ThemePackage;

public interface ThemeGalleryUseCase {

	ThemePackage publish(String userId, String name, String description, String packageJson, String modeDefault);

	List<ThemePackage> list(int limit);

	List<ThemePackage> mine(String userId);

	ThemePackage get(String id);

	void report(String themeId);

	void install(String themeId);

	void hide(String themeId, String userId);
}
