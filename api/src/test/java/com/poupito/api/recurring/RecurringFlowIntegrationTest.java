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
	private String cardId;
	private String expenseCategoryId;
	private String incomeCategoryId;

	@BeforeEach
	void setUp() throws Exception {
		ResponseEntity<String> register = rest.postForEntity("/v1/auth/register",
				Map.of("email", "fixos-" + UUID.randomUUID() + "@poupito.com", "password", "Senha-Forte-123"),
				String.class);
		headers = AuthTestSupport.bearer(register);
		accountId = idOf(post("/v1/accounts", Map.of("name", "Uniclass", "type", "CHECKING")));
		cardId = idOf(post("/v1/cards", Map.of("name", "Nubank", "accountId", accountId,
				"closingDay", 3, "dueDay", 10)));
		expenseCategoryId = idOf(post("/v1/categories", Map.of("name", "Assinaturas", "kind", "EXPENSE")));
		incomeCategoryId = idOf(post("/v1/categories", Map.of("name", "Salário", "kind", "INCOME")));
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

		// materializa julho → cria a ocorrência (transação não paga)
		JsonNode occurrences = objectMapper.readTree(
				rest.exchange("/v1/recurring/materialize?month=2026-07", HttpMethod.POST,
						new HttpEntity<>(headers), String.class).getBody());
		assertThat(occurrences.get(0).get("materialized").asBoolean()).isTrue();
		assertThat(occurrences.get(0).get("paid").asBoolean()).isFalse();
		String transactionId = occurrences.get(0).get("transactionId").asText();

		// aparece em transações com paid=false
		JsonNode july = objectMapper.readTree(get("/v1/transactions?month=2026-07").getBody());
		assertThat(july.get("totalElements").asLong()).isEqualTo(1);
		assertThat(july.get("content").get(0).get("paid").asBoolean()).isFalse();

		// materializar de novo é idempotente (não duplica)
		rest.exchange("/v1/recurring/materialize?month=2026-07", HttpMethod.POST,
				new HttpEntity<>(headers), String.class);
		assertThat(objectMapper.readTree(get("/v1/transactions?month=2026-07").getBody())
				.get("totalElements").asLong()).isEqualTo(1);

		// marca como pago
		ResponseEntity<String> paid = rest.exchange("/v1/transactions/" + transactionId + "/paid", HttpMethod.PUT,
				new HttpEntity<>(Map.of("paid", true), headers), String.class);
		assertThat(paid.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(objectMapper.readTree(paid.getBody()).get("paid").asBoolean()).isTrue();

		// ocorrências agora refletem pago
		JsonNode after = objectMapper.readTree(get("/v1/recurring/occurrences?month=2026-07").getBody());
		assertThat(after.get(0).get("paid").asBoolean()).isTrue();
	}

	@Test
	void shouldCreateRecurringOnCardAndLinkOccurrenceToInvoice() throws Exception {
		ResponseEntity<String> created = post("/v1/recurring",
				Map.of("description", "Netflix", "amount", "55.90", "type", "EXPENSE",
						"cardId", cardId, "categoryId", expenseCategoryId, "dayOfMonth", 10, "active", true));
		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		JsonNode body = objectMapper.readTree(created.getBody());
		assertThat(body.get("cardId").asText()).isEqualTo(cardId);
		assertThat(body.get("cardName").asText()).isEqualTo("Nubank");
		assertThat(body.get("method").asText()).isEqualTo("CREDITO");
		assertThat(body.get("accountId").isNull()).isTrue();

		// materializa julho: a ocorrência entra na fatura do cartão (fechamento dia 3 → fatura de agosto)
		JsonNode occurrences = objectMapper.readTree(
				rest.exchange("/v1/recurring/materialize?month=2026-07", HttpMethod.POST,
						new HttpEntity<>(headers), String.class).getBody());
		assertThat(occurrences.get(0).get("materialized").asBoolean()).isTrue();
		assertThat(occurrences.get(0).get("method").asText()).isEqualTo("CREDITO");
		// no cartão a quitação é o pagamento da fatura, então a ocorrência já nasce paga
		assertThat(occurrences.get(0).get("paid").asBoolean()).isTrue();

		// a transação criada está vinculada ao cartão
		JsonNode july = objectMapper.readTree(get("/v1/transactions?month=2026-07").getBody());
		JsonNode transaction = july.get("content").get(0);
		assertThat(transaction.get("cardId").asText()).isEqualTo(cardId);
		assertThat(transaction.get("method").asText()).isEqualTo("CREDITO");

		// e a fatura de agosto (fechamento dia 3) existe com o valor do fixo — era o ponto do bug:
		// gasto recorrente de cartão não chegava em Faturas porque nem podia ser cadastrado
		JsonNode invoices = objectMapper.readTree(get("/v1/invoices?month=2026-08").getBody());
		assertThat(invoices).hasSize(1);
		assertThat(invoices.get(0).get("cardName").asText()).isEqualTo("Nubank");
		assertThat(invoices.get(0).get("launchedTotal").decimalValue())
				.isEqualByComparingTo(new java.math.BigDecimal("55.90"));
	}

	@Test
	void shouldReturn400_whenNeitherAccountNorCardIsGiven() {
		ResponseEntity<String> response = post("/v1/recurring",
				Map.of("description", "x", "amount", "10.00", "type", "EXPENSE",
						"categoryId", expenseCategoryId, "dayOfMonth", 10));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("conta OU cartão");
	}

	@Test
	void shouldReturn400_whenBothAccountAndCardAreGiven() {
		ResponseEntity<String> response = post("/v1/recurring",
				Map.of("description", "x", "amount", "10.00", "type", "EXPENSE",
						"accountId", accountId, "cardId", cardId,
						"categoryId", expenseCategoryId, "dayOfMonth", 10));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void shouldReturn400_whenIncomeIsOnCard() {
		ResponseEntity<String> response = post("/v1/recurring",
				Map.of("description", "x", "amount", "10.00", "type", "INCOME",
						"cardId", cardId, "categoryId", incomeCategoryId, "dayOfMonth", 10));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
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
