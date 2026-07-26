package com.poupito.api.investment;

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
class InvestmentFlowIntegrationTest {

	@Autowired
	private TestRestTemplate rest;

	@Autowired
	private ObjectMapper objectMapper;

	private HttpHeaders headers;

	@BeforeEach
	void setUp() {
		String email = "investment-" + UUID.randomUUID() + "@poupito.com";
		ResponseEntity<String> register = rest.postForEntity("/v1/auth/register",
				Map.of("email", email, "password", "Senha-Forte-123"), String.class);
		headers = AuthTestSupport.bearer(register);
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

	@Test
	void shouldCreateInvestmentUpdateAndDelete() throws Exception {
		String investmentId = idOf(post("/v1/investments",
				Map.of("name", "Tesouro Selic", "assetClass", "RENDA_FIXA", "institution", "NuInvest")));

		ResponseEntity<String> updated = rest.exchange("/v1/investments/" + investmentId, HttpMethod.PUT,
				new HttpEntity<>(Map.of("name", "Tesouro IPCA", "institution", "Rico"), headers), String.class);
		assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(objectMapper.readTree(updated.getBody()).get("name").asText()).isEqualTo("Tesouro IPCA");

		ResponseEntity<Void> deleted = rest.exchange("/v1/investments/" + investmentId, HttpMethod.DELETE,
				new HttpEntity<>(headers), Void.class);
		assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		JsonNode list = objectMapper.readTree(get("/v1/investments").getBody());
		assertThat(list).isEmpty();
	}

	@Test
	void shouldRegisterEntriesAndComputeReport() throws Exception {
		String investmentId = idOf(post("/v1/investments",
				Map.of("name", "Tesouro Selic", "assetClass", "RENDA_FIXA", "institution", "NuInvest")));

		post("/v1/investments/" + investmentId + "/entries",
				Map.of("date", "2026-01-15", "type", "APORTE", "amount", "100.00"));
		post("/v1/investments/" + investmentId + "/entries",
				Map.of("date", "2026-01-01", "type", "ATUALIZACAO_SALDO", "amount", "0", "balanceAfter", "1000.00"));
		post("/v1/investments/" + investmentId + "/entries",
				Map.of("date", "2026-01-31", "type", "ATUALIZACAO_SALDO", "amount", "0", "balanceAfter", "1120.00"));

		JsonNode entries = objectMapper.readTree(get("/v1/investments/" + investmentId + "/entries").getBody());
		assertThat(entries).hasSize(3);

		JsonNode report = objectMapper.readTree(get("/v1/investments/report").getBody());
		JsonNode investmentReport = report.get("investments").get(0);
		assertThat(investmentReport.get("currentBalance").asText()).isEqualTo("1120.0");
		assertThat(investmentReport.get("lastPeriodReturnPercentage").asDouble()).isEqualTo(2.00);
		assertThat(report.get("byClass").get(0).get("assetClass").asText()).isEqualTo("RENDA_FIXA");
	}

	@Test
	void shouldReturn400_whenAtualizacaoSaldoMissingBalanceAfter() throws Exception {
		String investmentId = idOf(post("/v1/investments",
				Map.of("name", "Tesouro Selic", "assetClass", "RENDA_FIXA", "institution", "NuInvest")));

		ResponseEntity<String> response = post("/v1/investments/" + investmentId + "/entries",
				Map.of("date", "2026-01-05", "type", "ATUALIZACAO_SALDO", "amount", "0"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void shouldDeleteEntry() throws Exception {
		String investmentId = idOf(post("/v1/investments",
				Map.of("name", "Tesouro Selic", "assetClass", "RENDA_FIXA", "institution", "NuInvest")));
		String entryId = idOf(post("/v1/investments/" + investmentId + "/entries",
				Map.of("date", "2026-01-05", "type", "APORTE", "amount", "1000.00")));

		ResponseEntity<Void> deleted = rest.exchange("/v1/investments/" + investmentId + "/entries/" + entryId,
				HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
		assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		JsonNode entries = objectMapper.readTree(get("/v1/investments/" + investmentId + "/entries").getBody());
		assertThat(entries).isEmpty();
	}

	@Test
	void shouldReturn404_whenAccessingInvestmentOfAnotherUser() throws Exception {
		String investmentId = idOf(post("/v1/investments",
				Map.of("name", "Tesouro Selic", "assetClass", "RENDA_FIXA", "institution", "NuInvest")));

		String otherEmail = "other-" + UUID.randomUUID() + "@poupito.com";
		ResponseEntity<String> otherRegister = rest.postForEntity("/v1/auth/register",
				Map.of("email", otherEmail, "password", "Senha-Forte-123"), String.class);
		HttpHeaders otherHeaders = AuthTestSupport.bearer(otherRegister);

		ResponseEntity<String> response = rest.exchange("/v1/investments/" + investmentId, HttpMethod.PUT,
				new HttpEntity<>(Map.of("name", "Hack", "institution", "Hack"), otherHeaders), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void shouldReturn401_whenNotAuthenticated() {
		assertThat(rest.getForEntity("/v1/investments", String.class).getStatusCode())
				.isEqualTo(HttpStatus.UNAUTHORIZED);
	}

}
