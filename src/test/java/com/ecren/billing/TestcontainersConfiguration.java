package com.ecren.billing;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer<?> postgresContainer() {
		// Pinned to 16-alpine: matches the version in docker-compose.yml.
		// Using 'latest' risks silent breakage when Postgres releases a new major version.
		return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
	}

}
