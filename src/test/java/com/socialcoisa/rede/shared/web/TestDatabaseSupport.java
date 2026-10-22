package com.socialcoisa.rede.shared.web;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Liga o {@link TestDatabase} ao DataSource do contexto de teste. Esta em src/test e e encontrado
 * pelo scan de componentes do @SpringBootTest, entao nenhum teste precisa importa-lo a mao.
 */
@Component
public class TestDatabaseSupport {

	public TestDatabaseSupport(DataSource dataSource) {
		TestDatabase.install(new JdbcTemplate(dataSource));
	}
}