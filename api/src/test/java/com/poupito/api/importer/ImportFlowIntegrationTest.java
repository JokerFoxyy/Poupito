package com.poupito.api.importer;

import com.poupito.api.TestcontainersConfiguration;
import com.poupito.api.importer.support.SampleSpreadsheetFactory;
import com.poupito.api.support.AuthTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ImportFlowIntegrationTest {

	@Autowired
	private TestRestTemplate rest;

	@Autowired
	private ObjectMapper objectMapper;

	private HttpHeaders headers;

	@BeforeEach
	void setUp() {
		String email = "import-" + UUID.randomUUID() + "@poupito.com";
		ResponseEntity<String> register = rest.postForEntity("/v1/auth/register",
				Map.of("email", email, "password", "Senha-Forte-123"), String.class);
		headers = AuthTestSupport.bearer(register);
	}

	private ByteArrayResource sampleFileResource() throws Exception {
		byte[] bytes = SampleSpreadsheetFactory.julySample().readAllBytes();
		return new ByteArrayResource(bytes) {
			@Override
			public String getFilename() {
				return "planilha.xlsx";
			}
		};
	}

	@Test
	void shouldPreviewRowsAndListUnmatchedAccountsAndCategories() throws Exception {
		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("file", sampleFileResource());
		HttpHeaders multipartHeaders = new HttpHeaders(headers);
		multipartHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);

		ResponseEntity<String> response = rest.exchange("/v1/import/preview?year=2026", HttpMethod.POST,
				new HttpEntity<>(body, multipartHeaders), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode json = objectMapper.readTree(response.getBody());
		assertThat(json.get("rows")).hasSize(5);
		assertThat(json.get("unmatchedAccounts")).isNotEmpty();
		assertThat(json.get("unmatchedCategories")).isNotEmpty();
	}

	@Test
	void shouldCommitAndCreateTransactionsWithNewAccountsAndCategories() throws Exception {
		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("file", sampleFileResource());
		HttpHeaders multipartHeaders = new HttpHeaders(headers);
		multipartHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);

		ResponseEntity<String> response = rest.exchange("/v1/import/commit?year=2026", HttpMethod.POST,
				new HttpEntity<>(body, multipartHeaders), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode json = objectMapper.readTree(response.getBody());
		assertThat(json.get("transactionsCreated").asInt()).isEqualTo(5);
		assertThat(json.get("accountsCreated").asInt()).isGreaterThan(0);
		assertThat(json.get("categoriesCreated").asInt()).isGreaterThan(0);
	}

	@Test
	void shouldSkipDuplicates_whenCommittingTheSameFileTwice() throws Exception {
		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("file", sampleFileResource());
		HttpHeaders multipartHeaders = new HttpHeaders(headers);
		multipartHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);

		rest.exchange("/v1/import/commit?year=2026", HttpMethod.POST, new HttpEntity<>(body, multipartHeaders),
				String.class);

		MultiValueMap<String, Object> body2 = new LinkedMultiValueMap<>();
		body2.add("file", sampleFileResource());
		ResponseEntity<String> second = rest.exchange("/v1/import/commit?year=2026", HttpMethod.POST,
				new HttpEntity<>(body2, multipartHeaders), String.class);

		JsonNode json = objectMapper.readTree(second.getBody());
		assertThat(json.get("transactionsCreated").asInt()).isZero();
		assertThat(json.get("transactionsSkippedAsDuplicate").asInt()).isEqualTo(5);
	}

	@Test
	void shouldReturn401_whenNotAuthenticated() throws Exception {
		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("file", sampleFileResource());
		HttpHeaders multipartHeaders = new HttpHeaders();
		multipartHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);

		ResponseEntity<String> response = rest.exchange("/v1/import/preview?year=2026", HttpMethod.POST,
				new HttpEntity<>(body, multipartHeaders), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

}
