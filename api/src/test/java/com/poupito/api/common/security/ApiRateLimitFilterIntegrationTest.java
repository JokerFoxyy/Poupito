package com.poupito.api.common.security;

import com.poupito.api.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
		"app.security.api-rate-limit.default.max-requests=3",
		"app.security.api-rate-limit.default.window=PT1M",
		"app.security.api-rate-limit.expensive.max-requests=1",
		"app.security.api-rate-limit.expensive.window=PT1M"
})
class ApiRateLimitFilterIntegrationTest {

	@Autowired
	private TestRestTemplate rest;

	@Test
	void shouldReturn429WithRetryAfter_whenDefaultLimitExceeded() {
		for (int i = 0; i < 3; i++) {
			rest.getForEntity("/actuator/info", String.class);
		}

		ResponseEntity<String> response = rest.getForEntity("/actuator/info", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(response.getHeaders().getFirst("Retry-After")).isNotNull();
		assertThat(response.getBody()).contains("\"status\":429");
	}

	@Test
	void shouldReturn429_whenExpensiveEndpointLimitExceeded_evenBeforeDefaultLimit() {
		ResponseEntity<String> first = rest.getForEntity("/v1/transactions/export?month=2026-01", String.class);
		assertThat(first.getStatusCode()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);

		ResponseEntity<String> second = rest.getForEntity("/v1/transactions/export?month=2026-01", String.class);

		assertThat(second.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
	}

	@Test
	void shouldNotRateLimit_actuatorHealth() {
		for (int i = 0; i < 5; i++) {
			ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		}
	}

}
