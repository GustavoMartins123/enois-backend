package com.socialcoisa.rede.themes.application.port.out;

import java.util.List;
import java.util.Optional;

import com.socialcoisa.rede.themes.domain.ThemeGalleryStatus;
import com.socialcoisa.rede.themes.domain.ThemePackage;

public interface ThemeGalleryRepository {

	ThemePackage save(ThemePackage theme);

	Optional<ThemePackage> findById(String id);

	List<ThemePackage> findPublished(int limit);

	List<ThemePackage> findByAuthor(String authorId, int limit);
}
