package com.poupito.api.auth;

import com.poupito.api.TestcontainersConfiguration;
import com.poupito.api.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AuthFlowIntegrationTest {

	private static final String PASSWORD = "Senha-Forte-123";

	@Autowired
	private TestRestTemplate rest;

	private String uniqueEmail() {
		return "auth-" + UUID.randomUUID() + "@poupito.com";
	}

	private ResponseEntity<String> register(String email) {
		return rest.postForEntity("/v1/auth/register",
				Map.of("email", email, "password", PASSWORD), String.class);
	}

	@Test
	void shouldSetHttpOnlyCookiesAndReturnUserWithoutToken_whenRegistering() {
		ResponseEntity<String> response = register(uniqueEmail());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		String setCookie = String.join("\n", response.getHeaders().get(HttpHeaders.SET_COOKIE));
		assertThat(setCookie).contains("poupito_at=").contains("poupito_rt=")
				.contains("HttpOnly").contains("SameSite=Strict");
		assertThat(response.getBody()).contains("\"email\"").doesNotContain("token");
	}

	@Test
	void shouldRegisterLoginAndAccessMe_whenFlowIsValid() {
		String email = uniqueEmail();
		register(email);

		ResponseEntity<String> login = rest.postForEntity("/v1/auth/login",
				Map.of("email", email, "password", PASSWORD), String.class);
		assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);

		ResponseEntity<String> me = rest.exchange("/v1/auth/me", HttpMethod.GET,
				new HttpEntity<>(AuthTestSupport.bearer(login)), String.class);
		assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(me.getBody()).contains(email);
	}

	@Test
	void shouldReturn401_whenAccessingMeWithoutToken() {
		assertThat(rest.getForEntity("/v1/auth/me", String.class).getStatusCode())
				.isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void shouldReturn409_whenEmailIsAlreadyRegistered() {
		String email = uniqueEmail();
		register(email);

		assertThat(register(email).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
	}

	@Test
	void shouldReturn401_whenLoginPasswordIsWrong() {
		String email = uniqueEmail();
		register(email);

		ResponseEntity<String> login = rest.postForEntity("/v1/auth/login",
				Map.of("email", email, "password", "senha-errada-123"), String.class);

		assertThat(login.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void shouldReturn400_whenPasswordIsTooWeak() {
		ResponseEntity<String> response = rest.postForEntity("/v1/auth/register",
				Map.of("email", uniqueEmail(), "password", "semnumeros"), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("fieldErrors");
	}

	@Test
	void shouldRotateRefreshTokenAndRejectOldOne_whenRefreshing() {
		ResponseEntity<String> registration = register(uniqueEmail());

		ResponseEntity<String> refreshed = rest.exchange("/v1/auth/refresh", HttpMethod.POST,
				new HttpEntity<>(AuthTestSupport.refreshCookie(registration)), String.class);
		assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);

		ResponseEntity<String> reused = rest.exchange("/v1/auth/refresh", HttpMethod.POST,
				new HttpEntity<>(AuthTestSupport.refreshCookie(registration)), String.class);
		assertThat(reused.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

		ResponseEntity<String> again = rest.exchange("/v1/auth/refresh", HttpMethod.POST,
				new HttpEntity<>(AuthTestSupport.refreshCookie(refreshed)), String.class);
		assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void shouldRevokeSession_whenLoggingOut() {
		ResponseEntity<String> registration = register(uniqueEmail());

		ResponseEntity<Void> logout = rest.exchange("/v1/auth/logout", HttpMethod.POST,
				new HttpEntity<>(AuthTestSupport.refreshCookie(registration)), Void.class);
		assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		ResponseEntity<String> refresh = rest.exchange("/v1/auth/refresh", HttpMethod.POST,
				new HttpEntity<>(AuthTestSupport.refreshCookie(registration)), String.class);
		assertThat(refresh.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void shouldRateLimit_whenTooManyLoginAttempts() {
		String email = uniqueEmail();
		register(email);
		Map<String, String> wrong = Map.of("email", email, "password", "senha-errada-123");

		HttpStatusCode last = null;
		for (int i = 0; i < 15; i++) {
			last = rest.postForEntity("/v1/auth/login", wrong, String.class).getStatusCode();
		}

		assertThat(last).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
	}

	@Test
	void shouldKeepHealthAndApiDocsPublic_whenNotAuthenticated() {
		assertThat(rest.getForEntity("/actuator/health", String.class).getStatusCode())
				.isEqualTo(HttpStatus.OK);
		assertThat(rest.getForEntity("/v3/api-docs", String.class).getStatusCode())
				.isEqualTo(HttpStatus.OK);
	}

}
