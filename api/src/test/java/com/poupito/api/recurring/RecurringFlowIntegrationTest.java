package com.poupito.api.recurring;

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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class RecurringFlowIntegrationTest {

	@Autowired
	private TestRestTemplate rest;

	@Autowired
	private ObjectMapper objectMapper;

	private HttpHeaders headers;
	private String accountId;
	private String expenseCategoryId;
	private String incomeCategoryId;

	@BeforeEach
	void setUp() throws Exception {
		ResponseEntity<String> register = rest.postForEntity("/v1/auth/register",
				Map.of("email", "fixos-" + UUID.randomUUID() + "@poupito.com", "password", "Senha-Forte-123"),
				String.class);
		headers = AuthTestSupport.bearer(register);
		accountId = idOf(post("/v1/accounts", Map.of("name", "Uniclass", "type", "CHECKING")));
		expenseCategoryId = idOf(post("/v1/categories", Map.of("name", "Assinaturas", "kind", "EXPENSE")));
		incomeCategoryId = idOf(post("/v1/categories", Map.of("name", "SalÃ¡rio", "kind", "INCOME")));
	}

	private ResponseEntity<String> post(String url, Map<String, ?> body) {
		return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
	}

	private ResponseEntity<String> get(String url) {
		return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
	}

	private String idOf(ResponseEntity<String> response) throws Exception {
		return objectMapper.readTree(response.getBody()).get("id").asText();
	}

	private Map<String, Object> fixo(String description, String type, String categoryId, int day) {
		return Map.of("description", description, "amount", "27.90", "type", type,
				"accountId", accountId, "categoryId", categoryId, "dayOfMonth", day, "active", true);
	}

	@Test
	void shouldCompleteRecurringCrudFlow() throws Exception {
		ResponseEntity<String> created = post("/v1/recurring", fixo("Spotify", "EXPENSE", expenseCategoryId, 10));
		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		String id = idOf(created);

		assertThat(get("/v1/recurring").getBody()).contains("Spotify");

		ResponseEntity<String> updated = rest.exchange("/v1/recurring/" + id, HttpMethod.PUT,
				new HttpEntity<>(fixo("Spotify Family", "EXPENSE", expenseCategoryId, 12), headers), String.class);
		assertThat(updated.getBody()).contains("Spotify Family");

		ResponseEntity<Void> deleted = rest.exchange("/v1/recurring/" + id, HttpMethod.DELETE,
				new HttpEntity<>(headers), Void.class);
		assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
	}

	@Test
	void shouldMaterializeMarkPaidAndReflectInTransactions() throws Exception {
		post("/v1/recurring", fixo("Spotify", "EXPENSE", expenseCategoryId, 10));

		// materializa julho â†’ cria a ocorrÃªncia (transaÃ§Ã£o nÃ£o paga)
		JsonNode occurrences = objectMapper.readTree(
				rest.exchange("/v1/recurring/materialize?month=2026-07", HttpMethod.POST,
						new HttpEntity<>(headers), String.class).getBody());
		assertThat(occurrences.get(0).get("materialized").asBoolean()).isTrue();
		assertThat(occurrences.get(0).get("paid").asBoolean()).isFalse();
		String transactionId = occurrences.get(0).get("transactionId").asText();

		// aparece em transaÃ§Ãµes com paid=false
		JsonNode july = objectMapper.readTree(get("/v1/transactions?month=2026-07").getBody());
		assertThat(july.get("totalElements").asLong()).isEqualTo(1);
		assertThat(july.get("content").get(0).get("paid").asBoolean()).isFalse();

		// materializar de novo Ã© idempotente (nÃ£o duplica)
		rest.exchange("/v1/recurring/materialize?month=2026-07", HttpMethod.POST,
				new HttpEntity<>(headers), String.class);
		assertThat(objectMapper.readTree(get("/v1/transactions?month=2026-07").getBody())
				.get("totalElements").asLong()).isEqualTo(1);

		// marca como pago
		ResponseEntity<String> paid = rest.exchange("/v1/transactions/" + transactionId + "/paid", HttpMethod.PUT,
				new HttpEntity<>(Map.of("paid", true), headers), String.class);
		assertThat(paid.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(objectMapper.readTree(paid.getBody()).get("paid").asBoolean()).isTrue();

		// ocorrÃªncias agora refletem pago
		JsonNode after = objectMapper.readTree(get("/v1/recurring/occurrences?month=2026-07").getBody());
		assertThat(after.get(0).get("paid").asBoolean()).isTrue();
	}

	@Test
	void shouldReturn400_whenCategoryKindDoesNotMatchType() {
		ResponseEntity<String> response = post("/v1/recurring",
				fixo("Errado", "EXPENSE", incomeCategoryId, 10));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void shouldReturn400_whenDayOfMonthIsInvalid() {
		ResponseEntity<String> response = post("/v1/recurring",
				Map.of("description", "x", "amount", "10.00", "type", "EXPENSE",
						"accountId", accountId, "categoryId", expenseCategoryId, "dayOfMonth", 40));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void shouldReturn404_whenUpdatingUnknownRecurring() {
		ResponseEntity<String> response = rest.exchange("/v1/recurring/" + UUID.randomUUID(), HttpMethod.PUT,
				new HttpEntity<>(fixo("x", "EXPENSE", expenseCategoryId, 10), headers), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void shouldReturn401_whenNotAuthenticated() {
		assertThat(rest.getForEntity("/v1/recurring", String.class).getStatusCode())
				.isEqualTo(HttpStatus.UNAUTHORIZED);
	}

}
