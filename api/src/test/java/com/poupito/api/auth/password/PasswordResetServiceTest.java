package com.poupito.api.auth.password;

import com.poupito.api.common.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

	private final UUID userId = UUID.randomUUID();

	@Mock
	private PasswordResetTokenRepository repository;

	private PasswordResetService service;

	@BeforeEach
	void setUp() {
		service = new PasswordResetService(repository, Duration.ofMinutes(30));
	}

	@Test
	void shouldIssueRawTokenPersistHashAndInvalidatePrevious() {
		when(repository.save(any(PasswordResetToken.class))).thenAnswer(inv -> inv.getArgument(0));

		String raw = service.issue(userId);

		assertThat(raw).isNotBlank();
		verify(repository).deleteByUserIdAndUsedAtIsNull(userId);
		verify(repository).save(any(PasswordResetToken.class));
	}

	@Test
	void shouldConsumeValidTokenAndMarkUsed() {
		PasswordResetToken token = new PasswordResetToken(userId, "hash",
				Instant.now().plus(Duration.ofMinutes(10)));
		when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

		UUID result = service.consume("raw-token");

		assertThat(result).isEqualTo(userId);
		assertThat(token.getUsedAt()).isNotNull();
	}

	@Test
	void shouldThrow_whenTokenIsBlank() {
		assertThatThrownBy(() -> service.consume("  ")).isInstanceOf(BusinessException.class);
		verify(repository, never()).findByTokenHash(anyString());
	}

	@Test
	void shouldThrow_whenTokenNotFound() {
		when(repository.findByTokenHash(anyString())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.consume("raw-token")).isInstanceOf(BusinessException.class);
	}

	@Test
	void shouldThrow_whenTokenExpired() {
		PasswordResetToken expired = new PasswordResetToken(userId, "hash",
				Instant.now().minus(Duration.ofMinutes(1)));
		when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));

		assertThatThrownBy(() -> service.consume("raw-token")).isInstanceOf(BusinessException.class);
	}

	@Test
	void shouldThrow_whenTokenAlreadyUsed() {
		PasswordResetToken used = new PasswordResetToken(userId, "hash",
				Instant.now().plus(Duration.ofMinutes(10)));
		used.markUsed(Instant.now());
		when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(used));

		assertThatThrownBy(() -> service.consume("raw-token")).isInstanceOf(BusinessException.class);
	}

}
