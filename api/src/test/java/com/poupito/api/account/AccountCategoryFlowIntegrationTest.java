package com.poupito.api.account;

import com.poupito.api.TestcontainersConfiguration;
import com.poupito.api.support.AuthTestSupport;
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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AccountCategoryFlowIntegrationTest {

	@Autowired
	private TestRestTemplate rest;

	private HttpHeaders headers;

	@BeforeEach
	void authenticate() {
		String email = "contas-" + UUID.randomUUID() + "@poupito.com";
		ResponseEntity<String> register = rest.postForEntity("/v1/auth/register",
				Map.of("email", email, "password", "Senha-Forte-123"), String.class);
		headers = AuthTestSupport.bearer(register);
	}

	private <T> ResponseEntity<T> exchange(HttpMethod method, String url, Object body, Class<T> type) {
		return rest.exchange(url, method, new HttpEntity<>(body, headers), type);
	}

	@Test
	void shouldCompleteAccountCrudFlow_whenAuthenticated() {
		ResponseEntity<String> created = exchange(HttpMethod.POST, "/v1/accounts",
				Map.of("name", "Uniclass", "type", "CHECKING"), String.class);
		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		String id = created.getBody().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

		ResponseEntity<String> list = exchange(HttpMethod.GET, "/v1/accounts", null, String.class);
		assertThat(list.getBody()).contains("Uniclass").contains("\"type\":\"CHECKING\"");

		ResponseEntity<String> updated = exchange(HttpMethod.PUT, "/v1/accounts/" + id,
				Map.of("name", "Uniclass Plus", "type", "CHECKING"), String.class);
		assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(updated.getBody()).contains("Uniclass Plus");

		ResponseEntity<Void> deleted = exchange(HttpMethod.DELETE, "/v1/accounts/" + id, null, Void.class);
		assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		ResponseEntity<String> afterDelete = exchange(HttpMethod.GET, "/v1/accounts", null, String.class);
		assertThat(afterDelete.getBody()).doesNotContain("Uniclass");
	}

	@Test
	void shouldCompleteCardCrudFlow_linkedToAccount() {
		ResponseEntity<String> account = exchange(HttpMethod.POST, "/v1/accounts",
				Map.of("name", "Nubank Conta", "type", "CHECKING"), String.class);
		String accountId = account.getBody().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

		ResponseEntity<String> created = exchange(HttpMethod.POST, "/v1/cards",
				Map.of("name", "Nubank", "accountId", accountId, "closingDay", 28, "dueDay", 7), String.class);
		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(created.getBody()).contains("Nubank").contains("\"closingDay\":28")
				.contains("\"accountName\":\"Nubank Conta\"");
		String cardId = created.getBody().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

		ResponseEntity<String> list = exchange(HttpMethod.GET, "/v1/cards", null, String.class);
		assertThat(list.getBody()).contains("Nubank");

		ResponseEntity<Void> deleted = exchange(HttpMethod.DELETE, "/v1/cards/" + cardId, null, Void.class);
		assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
	}

	@Test
	void shouldReturn400_whenCardHasNoInvoiceDays() {
		ResponseEntity<String> account = exchange(HttpMethod.POST, "/v1/accounts",
				Map.of("name", "Conta", "type", "CHECKING"), String.class);
		String accountId = account.getBody().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

		ResponseEntity<String> response = exchange(HttpMethod.POST, "/v1/cards",
				Map.of("name", "Black", "accountId", accountId), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void shouldCompleteCategoryCrudFlow_whenAuthenticated() {
		ResponseEntity<String> created = exchange(HttpMethod.POST, "/v1/categories",
				Map.of("name", "Mercado", "icon", "ðŸ›’", "color", "#3fb950", "kind", "EXPENSE"), String.class);
		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		String id = created.getBody().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

		ResponseEntity<String> duplicate = exchange(HttpMethod.POST, "/v1/categories",
				Map.of("name", "mercado", "kind", "EXPENSE"), String.class);
		assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

		ResponseEntity<String> updated = exchange(HttpMethod.PUT, "/v1/categories/" + id,
				Map.of("name", "Supermercado", "icon", "ðŸ›’", "color", "#d29922", "kind", "EXPENSE"), String.class);
		assertThat(updated.getBody()).contains("Supermercado").contains("#d29922");

		ResponseEntity<Void> deleted = exchange(HttpMethod.DELETE, "/v1/categories/" + id, null, Void.class);
		assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
	}

	@Test
	void shouldReturn400_whenCategoryColorIsInvalid() {
		ResponseEntity<String> response = exchange(HttpMethod.POST, "/v1/categories",
				Map.of("name", "Lazer", "color", "azul", "kind", "EXPENSE"), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("fieldErrors");
	}

	@Test
	void shouldReturn404_whenAccessingResourceOfAnotherUser() {
		ResponseEntity<String> created = exchange(HttpMethod.POST, "/v1/accounts",
				Map.of("name", "Uniclass", "type", "CHECKING"), String.class);
		String id = created.getBody().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

		// segundo usuÃ¡rio tenta acessar a conta do primeiro
		String otherEmail = "intruso-" + UUID.randomUUID() + "@poupito.com";
		ResponseEntity<String> other = rest.postForEntity("/v1/auth/register",
				Map.of("email", otherEmail, "password", "Senha-Forte-123"), String.class);
		HttpHeaders otherHeaders = AuthTestSupport.bearer(other);

		ResponseEntity<String> stolen = rest.exchange("/v1/accounts/" + id, HttpMethod.PUT,
				new HttpEntity<>(Map.of("name", "Hackeada", "type", "CHECKING"), otherHeaders), String.class);
		assertThat(stolen.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void shouldReturn401_whenNotAuthenticated() {
		assertThat(rest.getForEntity("/v1/accounts", String.class).getStatusCode())
				.isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(rest.getForEntity("/v1/categories", String.class).getStatusCode())
				.isEqualTo(HttpStatus.UNAUTHORIZED);
	}

}
