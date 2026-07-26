package com.poupito.api.account;

import com.poupito.api.TestcontainersConfiguration;
import com.poupito.api.support.AuthTestSupport;
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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class LgpdFlowIntegrationTest {

	@Autowired
	private TestRestTemplate rest;

	private HttpHeaders auth;

	private HttpHeaders setUpUserWithData() {
		ResponseEntity<String> register = rest.postForEntity("/v1/auth/register",
				Map.of("email", "lgpd-" + UUID.randomUUID() + "@poupito.com", "password", "Senha-Forte-123"),
				String.class);
		HttpHeaders headers = AuthTestSupport.bearer(register);
		String accountId = idOf(rest.exchange("/v1/accounts", HttpMethod.POST,
				new HttpEntity<>(Map.of("name", "Uniclass", "type", "CHECKING"), headers), String.class));
		String categoryId = idOf(rest.exchange("/v1/categories", HttpMethod.POST,
				new HttpEntity<>(Map.of("name", "Mercado", "kind", "EXPENSE"), headers), String.class));
		rest.exchange("/v1/transactions", HttpMethod.POST, new HttpEntity<>(Map.of(
				"description", "Padaria", "amount", "31.73", "date", "2026-07-09",
				"type", "EXPENSE", "accountId", accountId, "categoryId", categoryId), headers), String.class);
		return headers;
	}

	private String idOf(ResponseEntity<String> response) {
		return response.getBody().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
	}

	@Test
	void shouldExportAllUserData_whenRequested() {
		auth = setUpUserWithData();

		ResponseEntity<String> export = rest.exchange("/v1/account/export", HttpMethod.GET,
				new HttpEntity<>(auth), String.class);

		assertThat(export.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(export.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("attachment");
		assertThat(export.getBody()).contains("Uniclass").contains("Mercado").contains("Padaria");
	}

	@Test
	void shouldDeleteAccountAndInvalidateSession_whenRequested() {
		auth = setUpUserWithData();

		ResponseEntity<Void> deleted = rest.exchange("/v1/account", HttpMethod.DELETE,
				new HttpEntity<>(auth), Void.class);
		assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		// o token de acesso deixa de valer: o filtro nÃ£o acha mais o usuÃ¡rio
		ResponseEntity<String> me = rest.exchange("/v1/auth/me", HttpMethod.GET,
				new HttpEntity<>(auth), String.class);
		assertThat(me.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void shouldReturn401_whenExportingWithoutAuth() {
		assertThat(rest.getForEntity("/v1/account/export", String.class).getStatusCode())
				.isEqualTo(HttpStatus.UNAUTHORIZED);
	}

}
