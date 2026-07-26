package com.poupito.api.cdi;

import com.poupito.api.TestcontainersConfiguration;
import com.poupito.api.support.AuthTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class CdiFlowIntegrationTest {

	@Autowired
	private TestRestTemplate rest;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private BacenCdiClient bacenCdiClient;

	private HttpHeaders headers;

	@BeforeEach
	void setUp() {
		String email = "cdi-" + UUID.randomUUID() + "@poupito.com";
		ResponseEntity<String> register = rest.postForEntity("/v1/auth/register",
				Map.of("email", email, "password", "Senha-Forte-123"), String.class);
		headers = AuthTestSupport.bearer(register);
	}

	@Test
	void shouldReturnAccumulatedSeries_fetchingFromClientOnFirstCall() throws Exception {
		LocalDate from = LocalDate.of(2020, 1, 2);
		LocalDate to = LocalDate.of(2020, 1, 3);
		when(bacenCdiClient.fetchSeries(any(), any())).thenReturn(List.of(
				new CdiRate(from, new BigDecimal("0.038932")), new CdiRate(to, new BigDecimal("0.039000"))));

		ResponseEntity<String> response = rest.exchange(
				"/v1/investments/cdi?from=2020-01-02&to=2020-01-03", HttpMethod.GET,
				new HttpEntity<>(headers), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode body = objectMapper.readTree(response.getBody());
		assertThat(body).hasSize(2);
		assertThat(body.get(0).get("date").asText()).isEqualTo("2020-01-02");
	}

	@Test
	void shouldReturn400_whenFromIsAfterTo() {
		ResponseEntity<String> response = rest.exchange(
				"/v1/investments/cdi?from=2026-01-05&to=2026-01-02", HttpMethod.GET,
				new HttpEntity<>(headers), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void shouldReturn401_whenNotAuthenticated() {
		assertThat(rest.getForEntity("/v1/investments/cdi?from=2026-01-02&to=2026-01-05", String.class)
				.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

}
