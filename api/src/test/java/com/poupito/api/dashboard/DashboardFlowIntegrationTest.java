package com.poupito.api.dashboard;

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
class DashboardFlowIntegrationTest {

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
		String email = "dashboard-" + UUID.randomUUID() + "@poupito.com";
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
	void shouldComputeSummaryWithIncomeExpenseAndBalances() throws Exception {
		post("/v1/transactions", Map.of("description", "SalÃ¡rio", "amount", "5000.00", "date", "2026-07-05",
				"type", "INCOME", "accountId", checkingId, "categoryId", incomeCategoryId));
		post("/v1/transactions", Map.of("description", "Mercado", "amount", "300.00", "date", "2026-07-10",
				"type", "EXPENSE", "accountId", checkingId, "categoryId", expenseCategoryId));

		JsonNode summary = objectMapper.readTree(get("/v1/dashboard/summary?month=2026-07").getBody());

		assertThat(summary.get("income").asText()).isEqualTo("5000.0");
		assertThat(summary.get("expense").asText()).isEqualTo("300.0");
		assertThat(summary.get("monthBalance").asText()).isEqualTo("4700.0");
		assertThat(summary.get("cumulativeBalance").asText()).isEqualTo("4700.0");
		assertThat(summary.get("categorySpend")).hasSize(1);
		assertThat(summary.get("categorySpend").get(0).get("categoryName").asText()).isEqualTo("Mercado");
	}

	@Test
	void shouldIncludeBudgetReportInSummary() throws Exception {
		post("/v1/budgets", Map.of("categoryId", expenseCategoryId, "month", "2026-07", "amount", "500.00"));
		post("/v1/transactions", Map.of("description", "Mercado", "amount", "300.00", "date", "2026-07-10",
				"type", "EXPENSE", "accountId", checkingId, "categoryId", expenseCategoryId));

		JsonNode summary = objectMapper.readTree(get("/v1/dashboard/summary?month=2026-07").getBody());

		assertThat(summary.get("budgetReport")).hasSize(1);
		assertThat(summary.get("budgetReport").get(0).get("spent").asText()).isEqualTo("300.0");
	}

	@Test
	void shouldAccumulateBalanceAcrossMonths() throws Exception {
		post("/v1/transactions", Map.of("description", "SalÃ¡rio junho", "amount", "1000.00", "date", "2026-06-05",
				"type", "INCOME", "accountId", checkingId, "categoryId", incomeCategoryId));
		post("/v1/transactions", Map.of("description", "SalÃ¡rio julho", "amount", "1000.00", "date", "2026-07-05",
				"type", "INCOME", "accountId", checkingId, "categoryId", incomeCategoryId));

		JsonNode july = objectMapper.readTree(get("/v1/dashboard/summary?month=2026-07").getBody());

		assertThat(july.get("income").asText()).isEqualTo("1000.0");
		assertThat(july.get("cumulativeBalance").asText()).isEqualTo("2000.0");
	}

	@Test
	void shouldReturnAnnualSeriesUpToSelectedMonth() throws Exception {
		post("/v1/transactions", Map.of("description", "SalÃ¡rio", "amount", "1000.00", "date", "2026-02-05",
				"type", "INCOME", "accountId", checkingId, "categoryId", incomeCategoryId));
		post("/v1/transactions", Map.of("description", "Mercado", "amount", "200.00", "date", "2026-03-10",
				"type", "EXPENSE", "accountId", checkingId, "categoryId", expenseCategoryId));

		JsonNode annual = objectMapper.readTree(get("/v1/dashboard/annual?month=2026-03").getBody());

		assertThat(annual).hasSize(3);
		assertThat(annual.get(0).get("month").asText()).isEqualTo("2026-01");
		assertThat(annual.get(1).get("income").asText()).isEqualTo("1000.0");
		assertThat(annual.get(2).get("expense").asText()).isEqualTo("200.0");
	}

	@Test
	void shouldReturn401_whenNotAuthenticated() {
		assertThat(rest.getForEntity("/v1/dashboard/summary?month=2026-07", String.class).getStatusCode())
				.isEqualTo(HttpStatus.UNAUTHORIZED);
	}

}
