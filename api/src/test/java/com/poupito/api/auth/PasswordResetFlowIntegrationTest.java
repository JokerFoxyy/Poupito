package com.poupito.api.auth;

import com.poupito.api.TestcontainersConfiguration;
import com.poupito.api.email.EmailSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({ TestcontainersConfiguration.class, PasswordResetFlowIntegrationTest.RecordingMailConfig.class })
class PasswordResetFlowIntegrationTest {

	private static final String PASSWORD = "Senha-Forte-123";
	private static final String NEW_PASSWORD = "NovaSenha-2026!";
	private static final Pattern TOKEN = Pattern.compile("token=(\\S+)");

	@Autowired
	private TestRestTemplate rest;

	@Autowired
	private RecordingEmailSender mailer;

	private String uniqueEmail() {
		return "reset-" + UUID.randomUUID() + "@poupito.com";
	}

	private void register(String email) {
		rest.postForEntity("/v1/auth/register",
				Map.of("email", email, "password", PASSWORD), String.class);
	}

	private ResponseEntity<String> forgot(String email) {
		return rest.postForEntity("/v1/auth/forgot-password", Map.of("email", email), String.class);
	}

	private ResponseEntity<String> login(String email, String password) {
		return rest.postForEntity("/v1/auth/login",
				Map.of("email", email, "password", password), String.class);
	}

	private String lastToken() {
		Matcher m = TOKEN.matcher(mailer.messages.get(mailer.messages.size() - 1).body());
		assertThat(m.find()).isTrue();
		return m.group(1);
	}

	@Test
	void shouldCompleteResetFlow_changingPasswordAndInvalidatingOldOne() {
		String email = uniqueEmail();
		register(email);

		assertThat(forgot(email).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		String token = lastToken();

		ResponseEntity<String> reset = rest.postForEntity("/v1/auth/reset-password",
				Map.of("token", token, "newPassword", NEW_PASSWORD), String.class);
		assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		assertThat(login(email, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(login(email, NEW_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void shouldRejectReusedToken_afterSuccessfulReset() {
		String email = uniqueEmail();
		register(email);
		forgot(email);
		String token = lastToken();

		rest.postForEntity("/v1/auth/reset-password",
				Map.of("token", token, "newPassword", NEW_PASSWORD), String.class);
		ResponseEntity<String> reuse = rest.postForEntity("/v1/auth/reset-password",
				Map.of("token", token, "newPassword", NEW_PASSWORD), String.class);

		assertThat(reuse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void shouldReturn204ButNotSendEmail_whenEmailUnknown() {
		int before = mailer.messages.size();

		ResponseEntity<String> response = forgot("desconhecido-" + UUID.randomUUID() + "@poupito.com");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(mailer.messages).hasSize(before);
	}

	@Test
	void shouldReturn400_whenResetTokenIsInvalid() {
		ResponseEntity<String> response = rest.postForEntity("/v1/auth/reset-password",
				Map.of("token", "nao-existe", "newPassword", NEW_PASSWORD), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void shouldReturn400_whenResetPasswordIsTooWeak() {
		String email = uniqueEmail();
		register(email);
		forgot(email);
		String token = lastToken();

		ResponseEntity<String> response = rest.postForEntity("/v1/auth/reset-password",
				Map.of("token", token, "newPassword", "fraca"), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void shouldLockAccountAfterFiveFailedLogins_returning429WithRetryAfter() {
		String email = uniqueEmail();
		register(email);
		Map<String, String> wrong = Map.of("email", email, "password", "Senha-Errada-999");

		HttpStatusCode fifth = null;
		for (int i = 0; i < 5; i++) {
			fifth = rest.postForEntity("/v1/auth/login", wrong, String.class).getStatusCode();
		}
		assertThat(fifth).isEqualTo(HttpStatus.UNAUTHORIZED);

		ResponseEntity<String> locked = rest.postForEntity("/v1/auth/login", wrong, String.class);
		assertThat(locked.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(locked.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNotNull();
	}

	record SentMessage(String to, String subject, String body) {
	}

	static class RecordingEmailSender implements EmailSender {
		final List<SentMessage> messages = new ArrayList<>();

		@Override
		public synchronized void send(String to, String subject, String body) {
			messages.add(new SentMessage(to, subject, body));
		}
	}

	@TestConfiguration
	static class RecordingMailConfig {
		@Bean
		@Primary
		RecordingEmailSender recordingEmailSender() {
			return new RecordingEmailSender();
		}
	}

}
