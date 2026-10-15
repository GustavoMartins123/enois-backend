package com.socialcoisa.rede.shared.persistence;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
}
