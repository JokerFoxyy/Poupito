package com.poupito.api.invoice;

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
class InvoiceFlowIntegrationTest {

	@Autowired
	private TestRestTemplate rest;

	@Autowired
	private ObjectMapper objectMapper;

	private HttpHeaders headers;
	private String cardId;
	private String categoryId;

	@BeforeEach
	void setUp() throws Exception {
		ResponseEntity<String> register = rest.postForEntity("/v1/auth/register",
				Map.of("email", "fatura-" + UUID.randomUUID() + "@poupito.com", "password", "Senha-Forte-123"),
				String.class);
		headers = AuthTestSupport.bearer(register);
		String accountId = idOf(post("/v1/accounts", Map.of("name", "Nubank Conta", "type", "CHECKING")));
		cardId = idOf(post("/v1/cards",
				Map.of("name", "Nubank", "accountId", accountId, "closingDay", 28, "dueDay", 7)));
		categoryId = idOf(post("/v1/categories", Map.of("name", "Mercado", "kind", "EXPENSE")));
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

	private void purchase(String amount, String date) {
		post("/v1/transactions", Map.of("description", "Compra", "amount", amount, "date", date,
				"type", "EXPENSE", "cardId", cardId, "categoryId", categoryId));
	}

	private String openInvoiceId() throws Exception {
		JsonNode invoices = objectMapper.readTree(get("/v1/invoices?month=2026-07").getBody());
		return invoices.get(0).get("id").asText();
	}

	@Test
	void shouldCreateAdjustmentOnClose_thenReduceWhenDetailed_thenPay() throws Exception {
		purchase("100.00", "2026-07-15");
		String invoiceId = openInvoiceId();

		// fecha declarando 150 â†’ gera ajuste de 50
		JsonNode closed = objectMapper.readTree(rest.exchange("/v1/invoices/" + invoiceId + "/close",
				HttpMethod.POST, new HttpEntity<>(Map.of("declaredTotal", "150.00"), headers), String.class)
				.getBody());
		assertThat(closed.get("invoice").get("status").asText()).isEqualTo("CLOSED");
		assertThat(closed.get("invoice").get("launchedTotal").asDouble()).isEqualTo(100.0);
		assertThat(closed.get("invoice").get("adjustment").asDouble()).isEqualTo(50.0);

		// detalha mais 30 â†’ ajuste cai para 20 (recalculado ao abrir o detalhe)
		purchase("30.00", "2026-07-16");
		JsonNode detail = objectMapper.readTree(get("/v1/invoices/" + invoiceId).getBody());
		assertThat(detail.get("invoice").get("launchedTotal").asDouble()).isEqualTo(130.0);
		assertThat(detail.get("invoice").get("adjustment").asDouble()).isEqualTo(20.0);

		// paga
		JsonNode paid = objectMapper.readTree(rest.exchange("/v1/invoices/" + invoiceId + "/pay",
				HttpMethod.POST, new HttpEntity<>(headers), String.class).getBody());
		assertThat(paid.get("invoice").get("status").asText()).isEqualTo("PAID");
	}

	@Test
	void shouldRemoveAdjustment_whenFullyDetailed() throws Exception {
		purchase("100.00", "2026-07-15");
		String invoiceId = openInvoiceId();
		rest.exchange("/v1/invoices/" + invoiceId + "/close", HttpMethod.POST,
				new HttpEntity<>(Map.of("declaredTotal", "150.00"), headers), String.class);

		// detalha exatamente os 50 que faltavam â†’ ajuste some
		purchase("50.00", "2026-07-16");
		JsonNode detail = objectMapper.readTree(get("/v1/invoices/" + invoiceId).getBody());

		assertThat(detail.get("invoice").get("adjustment").asDouble()).isEqualTo(0.0);
		assertThat(detail.get("transactions")).noneMatch(t -> t.get("type").asText().equals("INVOICE_ADJUSTMENT"));
	}

	@Test
	void shouldReturn400_whenPayingOpenInvoice() throws Exception {
		purchase("100.00", "2026-07-15");
		String invoiceId = openInvoiceId();

		ResponseEntity<String> response = rest.exchange("/v1/invoices/" + invoiceId + "/pay",
				HttpMethod.POST, new HttpEntity<>(headers), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void shouldReturn404_whenInvoiceDoesNotExist() {
		ResponseEntity<String> response = rest.exchange("/v1/invoices/" + UUID.randomUUID(),
				HttpMethod.GET, new HttpEntity<>(headers), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void shouldReturn401_whenNotAuthenticated() {
		assertThat(rest.getForEntity("/v1/invoices?month=2026-07", String.class).getStatusCode())
				.isEqualTo(HttpStatus.UNAUTHORIZED);
	}

}
