package com.poupito.api.goal;

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

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class GoalFlowIntegrationTest {

	@Autowired
	private TestRestTemplate rest;

	@Autowired
	private ObjectMapper objectMapper;

	private HttpHeaders headers;

	@BeforeEach
	void setUp() {
		String email = "goal-" + UUID.randomUUID() + "@poupito.com";
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
	void shouldCreateGoalAndComputeRequiredContributionFromContributions() throws Exception {
		String targetDate = YearMonth.now().plusMonths(5).atDay(1).toString();
		String goalId = idOf(post("/v1/goals",
				Map.of("name", "Reserva de emergência", "targetAmount", "12000.00", "targetDate", targetDate)));

		post("/v1/goals/" + goalId + "/contributions",
				Map.of("month", YearMonth.now().toString(), "amount", "7200.00"));

		JsonNode goals = objectMapper.readTree(get("/v1/goals").getBody());
		assertThat(goals).hasSize(1);
		assertThat(goals.get(0).get("accumulated").asText()).isEqualTo("7200.0");
		assertThat(goals.get(0).get("requiredMonthlyContribution").asText()).isEqualTo("960.0");
		assertThat(goals.get(0).get("progressPercentage").asInt()).isEqualTo(60);
	}

	@Test
	void shouldUpdateAndDeleteGoal() throws Exception {
		String targetDate = YearMonth.now().plusMonths(3).atDay(1).toString();
		String goalId = idOf(post("/v1/goals",
				Map.of("name", "Viagem", "targetAmount", "8000.00", "targetDate", targetDate)));

		ResponseEntity<String> updated = rest.exchange("/v1/goals/" + goalId, HttpMethod.PUT,
				new HttpEntity<>(Map.of("name", "Viagem 2027", "targetAmount", "9000.00", "targetDate", targetDate),
						headers),
				String.class);
		assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(objectMapper.readTree(updated.getBody()).get("name").asText()).isEqualTo("Viagem 2027");

		ResponseEntity<Void> deleted = rest.exchange("/v1/goals/" + goalId, HttpMethod.DELETE,
				new HttpEntity<>(headers), Void.class);
		assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		JsonNode goals = objectMapper.readTree(get("/v1/goals").getBody());
		assertThat(goals).isEmpty();
	}

	@Test
	void shouldDeleteContribution() throws Exception {
		String targetDate = YearMonth.now().plusMonths(3).atDay(1).toString();
		String goalId = idOf(post("/v1/goals",
				Map.of("name", "Viagem", "targetAmount", "8000.00", "targetDate", targetDate)));
		String contributionId = idOf(post("/v1/goals/" + goalId + "/contributions",
				Map.of("month", YearMonth.now().toString(), "amount", "500.00")));

		ResponseEntity<Void> deleted = rest.exchange("/v1/goals/" + goalId + "/contributions/" + contributionId,
				HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
		assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		JsonNode contributions = objectMapper.readTree(
				get("/v1/goals/" + goalId + "/contributions").getBody());
		assertThat(contributions).isEmpty();
	}

	@Test
	void shouldReturn404_whenAccessingGoalOfAnotherUser() throws Exception {
		String targetDate = YearMonth.now().plusMonths(3).atDay(1).toString();
		String goalId = idOf(post("/v1/goals",
				Map.of("name", "Viagem", "targetAmount", "8000.00", "targetDate", targetDate)));

		String otherEmail = "other-" + UUID.randomUUID() + "@poupito.com";
		ResponseEntity<String> otherRegister = rest.postForEntity("/v1/auth/register",
				Map.of("email", otherEmail, "password", "Senha-Forte-123"), String.class);
		HttpHeaders otherHeaders = AuthTestSupport.bearer(otherRegister);

		ResponseEntity<String> response = rest.exchange("/v1/goals/" + goalId, HttpMethod.PUT,
				new HttpEntity<>(Map.of("name", "Hack", "targetAmount", "1.00", "targetDate", targetDate),
						otherHeaders),
				String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void shouldReturn400_whenTargetDateIsInThePast() {
		ResponseEntity<String> response = post("/v1/goals",
				Map.of("name", "Viagem", "targetAmount", "8000.00", "targetDate", LocalDate.of(2020, 1, 1).toString()));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void shouldReturn401_whenNotAuthenticated() {
		assertThat(rest.getForEntity("/v1/goals", String.class).getStatusCode())
				.isEqualTo(HttpStatus.UNAUTHORIZED);
	}

}
