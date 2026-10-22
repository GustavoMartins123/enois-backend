package com.socialcoisa.rede.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * O H2 dos testes tem DDL transacional, entao nunca deixa uma migracao marcada como FAILED (o caso
 * do MariaDB de producao). Por isso a decisao de reparar e testada com o Flyway mockado.
 */
@ExtendWith(MockitoExtension.class)
class FlywayMigrationConfigTests {

	private static final Pattern VERSIONED_MIGRATION = Pattern.compile("^V([0-9]+(?:\\.[0-9]+)?)__.+\\.sql$");

	@Mock
	private Flyway flyway;

	@Mock
	private MigrationInfoService info;

	@Mock
	private MigrationInfo failed;

	@Mock
	private MigrationInfo applied;

	@Test
	void repairsTheFailedMarkerBeforeMigrating() {
		when(flyway.info()).thenReturn(info);
		when(info.all()).thenReturn(new MigrationInfo[] { failed, applied });
		when(failed.getState()).thenReturn(MigrationState.FAILED);
		when(failed.getVersion()).thenReturn(MigrationVersion.fromVersion("17"));
		when(failed.getDescription()).thenReturn("support and terms");
		when(applied.getState()).thenReturn(MigrationState.SUCCESS);

		FlywayMigrationConfig.migrateWithRepair(flyway);

		InOrder order = inOrder(flyway);
		order.verify(flyway).repair();
		order.verify(flyway).migrate();
	}

	@Test
	void doesNotTouchTheHistoryWhenNothingFailed() {
		when(flyway.info()).thenReturn(info);
		when(info.all()).thenReturn(new MigrationInfo[] { applied });
		when(applied.getState()).thenReturn(MigrationState.SUCCESS);

		FlywayMigrationConfig.migrateWithRepair(flyway);

		verify(flyway, never()).repair();
		verify(flyway).migrate();
	}

	@Test
	void migrationVersionsAreUnique() throws Exception {
		var resource = Objects.requireNonNull(getClass().getClassLoader().getResource("db/migration"));
		Map<String, List<String>> migrationsByVersion;
		try (var paths = Files.list(Path.of(resource.toURI()))) {
			migrationsByVersion = paths
				.map(path -> path.getFileName().toString())
				.map(VERSIONED_MIGRATION::matcher)
				.filter(matcher -> matcher.matches())
				.collect(Collectors.groupingBy(
					matcher -> matcher.group(1),
					TreeMap::new,
					Collectors.mapping(matcher -> matcher.group(), Collectors.toList())));
		}

		var duplicates = migrationsByVersion.entrySet().stream()
			.filter(entry -> entry.getValue().size() > 1)
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

		assertThat(duplicates).as("Flyway migration versions must be unique").isEmpty();
	}

	@Test
	void projectHtmlMigrationDeclaresUnicodeStorageExplicitly() throws Exception {
		var resource = Objects.requireNonNull(
			getClass().getClassLoader().getResource("db/migration/V117__widen_user_projects_html.sql"));
		String sql = Files.readString(Path.of(resource.toURI())).replaceAll("\\s+", " ").toLowerCase();

		assertThat(sql).contains("modify column html longtext character set utf8mb4 collate utf8mb4_unicode_ci");
	}
}
