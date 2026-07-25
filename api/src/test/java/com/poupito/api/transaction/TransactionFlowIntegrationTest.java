package com.poupito.api.transaction;

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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class TransactionFlowIntegrationTest {

	@Autowired
	private TestRestTemplate rest;

	@Autowired
	private ObjectMapper objectMapper;

	private HttpHeaders headers;
	private String checkingId;
	private String cardId;
	private String expenseCategoryId;
	private String incomeCategoryId;

	@BeforeEach
	void setUp() throws Exception {
		String email = "trans-" + UUID.randomUUID() + "@poupito.com";
		ResponseEntity<String> register = rest.postForEntity("/v1/auth/register",
				Map.of("email", email, "password", "Senha-Forte-123"), String.class);
		headers = AuthTestSupport.bearer(register);

		checkingId = idOf(post("/v1/accounts", Map.of("name", "Uniclass", "type", "CHECKING")));
		String cardAccountId = idOf(post("/v1/accounts", Map.of("name", "Nubank Conta", "type", "CHECKING")));
		cardId = idOf(post("/v1/cards",
				Map.of("name", "Nubank", "accountId", cardAccountId, "closingDay", 28, "dueDay", 7)));
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

	private Map<String, Object> transaction(String description, String amount, String date,
			String type, String accountId, String categoryId) {
		return Map.of("description", description, "amount", amount, "date", date,
				"type", type, "accountId", accountId, "categoryId", categoryId);
	}

	private Map<String, Object> cardTransaction(String description, String amount, String date,
			String type, String cardId, String categoryId) {
		return Map.of("description", description, "amount", amount, "date", date,
				"type", type, "cardId", cardId, "categoryId", categoryId);
	}

	private Map<String, Object> transactionWithTags(String description, String amount, String date,
			String type, String accountId, String categoryId, List<String> tags) {
		return Map.of("description", description, "amount", amount, "date", date,
				"type", type, "accountId", accountId, "categoryId", categoryId, "tags", tags);
	}

	@Test
	void shouldLinkCardPurchasesToCorrectInvoices_whenDatesCrossClosingDay() throws Exception {
		// antes do fechamento (dia 28) â†’ fatura de julho; no dia do fechamento â†’ fatura de agosto
		ResponseEntity<String> before = post("/v1/transactions",
				cardTransaction("Compra antes", "100.00", "2026-07-27", "EXPENSE", cardId, expenseCategoryId));
		ResponseEntity<String> onClosing = post("/v1/transactions",
				cardTransaction("Compra no fechamento", "50.00", "2026-07-28", "EXPENSE", cardId, expenseCategoryId));

		assertThat(before.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(objectMapper.readTree(before.getBody()).get("invoiceMonth").asText()).isEqualTo("2026-07-01");
		assertThat(objectMapper.readTree(onClosing.getBody()).get("invoiceMonth").asText()).isEqualTo("2026-08-01");
	}

	@Test
	void shouldNotLinkInvoice_whenAccountIsChecking() throws Exception {
		ResponseEntity<String> created = post("/v1/transactions",
				transaction("Padaria", "31.73", "2026-07-09", "EXPENSE", checkingId, expenseCategoryId));

		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(objectMapper.readTree(created.getBody()).get("invoiceMonth").isNull()).isTrue();
	}

	@Test
	void shouldFilterByMonthAccountAndCategory_whenSearching() throws Exception {
		post("/v1/transactions", transaction("Julho corrente", "10.00", "2026-07-05", "EXPENSE", checkingId, expenseCategoryId));
		post("/v1/transactions", cardTransaction("Julho cartÃ£o", "20.00", "2026-07-06", "EXPENSE", cardId, expenseCategoryId));
		post("/v1/transactions", transaction("SalÃ¡rio julho", "5000.00", "2026-07-01", "INCOME", checkingId, incomeCategoryId));
		post("/v1/transactions", transaction("Junho", "30.00", "2026-06-15", "EXPENSE", checkingId, expenseCategoryId));

		JsonNode july = objectMapper.readTree(get("/v1/transactions?month=2026-07").getBody());
		assertThat(july.get("totalElements").asLong()).isEqualTo(3);

		JsonNode julyCard = objectMapper.readTree(
				get("/v1/transactions?month=2026-07&cardId=" + cardId).getBody());
		assertThat(julyCard.get("totalElements").asLong()).isEqualTo(1);
		assertThat(julyCard.get("content").get(0).get("description").asText()).isEqualTo("Julho cartÃ£o");

		JsonNode julyIncome = objectMapper.readTree(
				get("/v1/transactions?month=2026-07&type=INCOME").getBody());
		assertThat(julyIncome.get("totalElements").asLong()).isEqualTo(1);

		JsonNode paged = objectMapper.readTree(
				get("/v1/transactions?month=2026-07&page=0&size=2").getBody());
		assertThat(paged.get("content").size()).isEqualTo(2);
		assertThat(paged.get("totalPages").asInt()).isEqualTo(2);
	}

	@Test
	void shouldNormalizeAndDedupeTags_whenCreating() throws Exception {
		ResponseEntity<String> created = post("/v1/transactions",
				transactionWithTags("Uber", "52.53", "2026-07-09", "EXPENSE", checkingId, expenseCategoryId,
						List.of(" Viagem ", "viagem", "Trabalho")));

		JsonNode tags = objectMapper.readTree(created.getBody()).get("tags");
		List<String> tagValues = objectMapper.convertValue(tags, List.class);
		assertThat(tagValues).containsExactlyInAnyOrder("viagem", "trabalho");
	}

	@Test
	void shouldFilterByFreeTextSearch() throws Exception {
		post("/v1/transactions", transaction("Padaria Sameiro", "31.73", "2026-07-05", "EXPENSE", checkingId, expenseCategoryId));
		post("/v1/transactions", transaction("Uber centro", "52.53", "2026-07-06", "EXPENSE", checkingId, expenseCategoryId));

		JsonNode result = objectMapper.readTree(get("/v1/transactions?month=2026-07&q=padaria").getBody());

		assertThat(result.get("totalElements").asLong()).isEqualTo(1);
		assertThat(result.get("content").get(0).get("description").asText()).isEqualTo("Padaria Sameiro");
	}

	@Test
	void shouldFilterByTag() throws Exception {
		post("/v1/transactions", transactionWithTags("Uber centro", "52.53", "2026-07-06", "EXPENSE",
				checkingId, expenseCategoryId, List.of("viagem")));
		post("/v1/transactions", transaction("Padaria", "31.73", "2026-07-05", "EXPENSE", checkingId, expenseCategoryId));

		JsonNode result = objectMapper.readTree(get("/v1/transactions?month=2026-07&tag=viagem").getBody());

		assertThat(result.get("totalElements").asLong()).isEqualTo(1);
		assertThat(result.get("content").get(0).get("description").asText()).isEqualTo("Uber centro");
	}

	@Test
	void shouldCreateInstallmentsOnConsecutiveMonths_linkedToTheirOwnInvoice() throws Exception {
		ResponseEntity<String> created = post("/v1/transactions", Map.of(
				"description", "Notebook", "amount", "500.00", "date", "2026-07-09", "type", "EXPENSE",
				"cardId", cardId, "categoryId", expenseCategoryId, "installments", 3));

		JsonNode first = objectMapper.readTree(created.getBody());
		assertThat(first.get("installmentNumber").asInt()).isEqualTo(1);
		assertThat(first.get("installmentCount").asInt()).isEqualTo(3);
		assertThat(first.get("invoiceMonth").asText()).isEqualTo("2026-07-01");

		JsonNode all = objectMapper.readTree(get("/v1/transactions?month=2026-09").getBody());
		assertThat(all.get("totalElements").asLong()).isEqualTo(1);
		JsonNode third = all.get("content").get(0);
		assertThat(third.get("installmentNumber").asInt()).isEqualTo(3);
		assertThat(third.get("date").asText()).isEqualTo("2026-09-09");
		assertThat(third.get("invoiceMonth").asText()).isEqualTo("2026-09-01");
	}

	@Test
	void shouldReturn400_whenInstallmentsRequestedForIncome() {
		ResponseEntity<String> response = post("/v1/transactions", Map.of(
				"description", "SalÃ¡rio", "amount", "1000.00", "date", "2026-07-09", "type", "INCOME",
				"accountId", checkingId, "categoryId", incomeCategoryId, "installments", 3));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void shouldDeleteOnlyFutureInstallments_whenScopeIsGroup() throws Exception {
		post("/v1/transactions", Map.of(
				"description", "Notebook", "amount", "100.00", "date", "2026-07-09", "type", "EXPENSE",
				"cardId", cardId, "categoryId", expenseCategoryId, "installments", 4));
		String secondId = objectMapper.readTree(get("/v1/transactions?month=2026-08").getBody())
				.get("content").get(0).get("id").asText();

		ResponseEntity<Void> deleted = rest.exchange("/v1/transactions/" + secondId + "?scope=group",
				HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
		assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		assertThat(objectMapper.readTree(get("/v1/transactions?month=2026-07").getBody())
				.get("totalElements").asLong()).isEqualTo(1);
		assertThat(objectMapper.readTree(get("/v1/transactions?month=2026-08").getBody())
				.get("totalElements").asLong()).isEqualTo(0);
		assertThat(objectMapper.readTree(get("/v1/transactions?month=2026-09").getBody())
				.get("totalElements").asLong()).isEqualTo(0);
		assertThat(objectMapper.readTree(get("/v1/transactions?month=2026-10").getBody())
				.get("totalElements").asLong()).isEqualTo(0);
	}

	@Test
	void shouldExportCsv_withOnlyFilteredTransactions() throws Exception {
		post("/v1/transactions", transaction("Padaria", "31.73", "2026-07-05", "EXPENSE", checkingId, expenseCategoryId));
		post("/v1/transactions", transaction("SalÃ¡rio", "5000.00", "2026-07-01", "INCOME", checkingId, incomeCategoryId));

		ResponseEntity<byte[]> response = rest.exchange(
				"/v1/transactions/export?month=2026-07&type=EXPENSE", HttpMethod.GET,
				new HttpEntity<>(headers), byte[].class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getHeaders().getContentDisposition().getFilename()).isEqualTo("transacoes-2026-07.csv");
		String csv = new String(response.getBody(), java.nio.charset.StandardCharsets.UTF_8);
		assertThat(csv).contains("Padaria");
		assertThat(csv).doesNotContain("SalÃ¡rio");
	}

	@Test
	void shouldExportXlsx_whenFormatIsXlsx() throws Exception {
		post("/v1/transactions", transaction("Padaria", "31.73", "2026-07-05", "EXPENSE", checkingId, expenseCategoryId));

		ResponseEntity<byte[]> response = rest.exchange(
				"/v1/transactions/export?month=2026-07&format=xlsx", HttpMethod.GET,
				new HttpEntity<>(headers), byte[].class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getHeaders().getContentType().toString())
				.isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
		assertThat(response.getHeaders().getContentDisposition().getFilename()).isEqualTo("transacoes-2026-07.xlsx");
		assertThat(response.getBody().length).isGreaterThan(0);
	}

	@Test
	void shouldReturn401_whenExportingWithoutAuthentication() {
		assertThat(rest.getForEntity("/v1/transactions/export?month=2026-07", String.class).getStatusCode())
				.isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void shouldReturn400_whenCategoryKindDoesNotMatchType() {
		ResponseEntity<String> response = post("/v1/transactions",
				transaction("Errado", "10.00", "2026-07-09", "EXPENSE", checkingId, incomeCategoryId));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void shouldReturn400_whenCreatingInvoiceAdjustmentManually() {
		ResponseEntity<String> response = post("/v1/transactions",
				transaction("Ajuste forjado", "10.00", "2026-07-09", "INVOICE_ADJUSTMENT", cardId, expenseCategoryId));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void shouldUpdateAndDeleteTransaction_whenOwnedByUser() throws Exception {
		String id = idOf(post("/v1/transactions",
				transaction("Padaria", "31.73", "2026-07-09", "EXPENSE", checkingId, expenseCategoryId)));

		ResponseEntity<String> updated = rest.exchange("/v1/transactions/" + id, HttpMethod.PUT,
				new HttpEntity<>(transaction("Padaria Sameiro", "35.00", "2026-07-10", "EXPENSE",
						checkingId, expenseCategoryId), headers), String.class);
		assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(updated.getBody()).contains("Padaria Sameiro");

		ResponseEntity<Void> deleted = rest.exchange("/v1/transactions/" + id, HttpMethod.DELETE,
				new HttpEntity<>(headers), Void.class);
		assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
	}

	@Test
	void shouldReturn409_whenDeletingAccountWithTransactions() {
		post("/v1/transactions", transaction("Padaria", "31.73", "2026-07-09", "EXPENSE", checkingId, expenseCategoryId));

		ResponseEntity<String> response = rest.exchange("/v1/accounts/" + checkingId, HttpMethod.DELETE,
				new HttpEntity<>(headers), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
	}

	@Test
	void shouldReturn401_whenNotAuthenticated() {
		assertThat(rest.getForEntity("/v1/transactions?month=2026-07", String.class).getStatusCode())
				.isEqualTo(HttpStatus.UNAUTHORIZED);
	}

}
