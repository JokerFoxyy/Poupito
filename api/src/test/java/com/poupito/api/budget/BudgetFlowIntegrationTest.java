package com.poupito.api.budget;

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
class BudgetFlowIntegrationTest {

	@Autowired
	private TestRestTemplate rest;

	@Autowired
	private ObjectMapper objectMapper;

	private HttpHeaders headers;
	private String checkingId;
	private String expenseCategoryId;
	private String incomeCategoryId;

	@BeforeEach
	void setUp() throws Exception {
		String email = "budget-" + UUID.randomUUID() + "@poupito.com";
		ResponseEntity<String> register = rest.postForEntity("/v1/auth/register",
				Map.of("email", email, "password", "Senha-Forte-123"), String.class);
		headers = AuthTestSupport.bearer(register);

		checkingId = idOf(post("/v1/accounts", Map.of("name", "Uniclass", "type", "CHECKING")));
		expenseCategoryId = idOf(post("/v1/categories", Map.of("name", "Mercado", "kind", "EXPENSE")));
		incomeCategoryId = idOf(post("/v1/categories", Map.of("name", "SalÃ¡rio", "kind", "INCOME")));
	}

	private ResponseEntity<String> post(String url, Map<String, ?> body) {
		return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
	}

	private ResponseEntity<String> get(String url) {
		return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
	}

	private String idOf(ResponseEntity<String> response) throws RuntimeException {
		try {
			return objectMapper.readTree(response.getBody()).get("id").asText();
		} catch (Exception e) {
			throw new RuntimeException("Resposta sem id: " + response.getBody(), e);
		}
	}

	@Test
	void shouldCreateBudgetAndReportSpentFromTransactions() throws Exception {
		String budgetId = idOf(post("/v1/budgets",
				Map.of("categoryId", expenseCategoryId, "month", "2026-07", "amount", "500.00")));

		post("/v1/transactions", Map.of("description", "Compra 1", "amount", "300.00", "date", "2026-07-05",
				"type", "EXPENSE", "accountId", checkingId, "categoryId", expenseCategoryId));

		JsonNode report = objectMapper.readTree(get("/v1/budgets?month=2026-07").getBody());
		assertThat(report).hasSize(1);
		assertThat(report.get(0).get("id").asText()).isEqualTo(budgetId);
		assertThat(report.get(0).get("budgeted").asText()).isEqualTo("500.0");
		assertThat(report.get(0).get("spent").asText()).isEqualTo("300.0");
		assertThat(report.get(0).get("over").asBoolean()).isFalse();
	}

	@Test
	void shouldMarkOver_whenSpentExceedsBudgeted() throws Exception {
		post("/v1/budgets", Map.of("categoryId", expenseCategoryId, "month", "2026-07", "amount", "100.00"));
		post("/v1/transactions", Map.of("description", "Compra grande", "amount", "150.00", "date", "2026-07-05",
				"type", "EXPENSE", "accountId", checkingId, "categoryId", expenseCategoryId));

		JsonNode report = objectMapper.readTree(get("/v1/budgets?month=2026-07").getBody());
		assertThat(report.get(0).get("over").asBoolean()).isTrue();
	}

	@Test
	void shouldReturn409_whenBudgetAlreadyExistsForCategoryAndMonth() {
		post("/v1/budgets", Map.of("categoryId", expenseCategoryId, "month", "2026-07", "amount", "500.00"));

		ResponseEntity<String> response = post("/v1/budgets",
				Map.of("categoryId", expenseCategoryId, "month", "2026-07", "amount", "200.00"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
	}

	@Test
	void shouldReturnOnlyOverBudgetCategories_inAlertsEndpoint() throws Exception {
		post("/v1/budgets", Map.of("categoryId", expenseCategoryId, "month", "2026-07", "amount", "100.00"));
		post("/v1/transactions", Map.of("description", "Compra grande", "amount", "150.00", "date", "2026-07-05",
				"type", "EXPENSE", "accountId", checkingId, "categoryId", expenseCategoryId));

		JsonNode alerts = objectMapper.readTree(get("/v1/budgets/alerts?month=2026-07").getBody());

		assertThat(alerts).hasSize(1);
		assertThat(alerts.get(0).get("over").asBoolean()).isTrue();
	}

	@Test
	void shouldReturn400_whenCategoryIsIncome() {
		ResponseEntity<String> response = post("/v1/budgets",
				Map.of("categoryId", incomeCategoryId, "month", "2026-07", "amount", "500.00"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void shouldUpdateAndDeleteBudget() throws Exception {
		String budgetId = idOf(post("/v1/budgets",
				Map.of("categoryId", expenseCategoryId, "month", "2026-07", "amount", "500.00")));

		ResponseEntity<String> updated = rest.exchange("/v1/budgets/" + budgetId, HttpMethod.PUT,
				new HttpEntity<>(Map.of("amount", "700.00"), headers), String.class);
		assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(objectMapper.readTree(updated.getBody()).get("budgeted").asText()).isEqualTo("700.0");

		ResponseEntity<Void> deleted = rest.exchange("/v1/budgets/" + budgetId, HttpMethod.DELETE,
				new HttpEntity<>(headers), Void.class);
		assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		JsonNode report = objectMapper.readTree(get("/v1/budgets?month=2026-07").getBody());
		assertThat(report).isEmpty();
	}

	@Test
	void shouldReturn404_whenUpdatingBudgetThatDoesNotExist() {
		ResponseEntity<String> response = rest.exchange("/v1/budgets/" + UUID.randomUUID(), HttpMethod.PUT,
				new HttpEntity<>(Map.of("amount", "700.00"), headers), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void shouldReturn401_whenNotAuthenticated() {
		assertThat(rest.getForEntity("/v1/budgets?month=2026-07", String.class).getStatusCode())
				.isEqualTo(HttpStatus.UNAUTHORIZED);
	}

}
