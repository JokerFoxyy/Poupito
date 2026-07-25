package com.poupito.api.auth;

import com.poupito.api.auth.dto.IssuedTokens;
import com.poupito.api.auth.password.PasswordResetMailer;
import com.poupito.api.auth.password.PasswordResetService;
import com.poupito.api.auth.refresh.RefreshTokenService;
import com.poupito.api.common.error.BusinessException;
import com.poupito.api.common.error.EmailAlreadyUsedException;
import com.poupito.api.common.error.InvalidCredentialsException;
import com.poupito.api.common.error.InvalidRefreshTokenException;
import com.poupito.api.user.User;
import com.poupito.api.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	private final UUID userId = UUID.randomUUID();

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private JwtService jwtService;

	@Mock
	private RefreshTokenService refreshTokenService;

	@Mock
	private PasswordResetService passwordResetService;

	@Mock
	private PasswordResetMailer passwordResetMailer;

	@InjectMocks
	private AuthService authService;

	private User userWithId(String email) {
		User user = new User(email, "hash");
		ReflectionTestUtils.setField(user, "id", userId);
		return user;
	}

	@Test
	void shouldRegisterAndReturnTokens_whenEmailIsNew() {
		when(userRepository.existsByEmail("victor@poupito.com")).thenReturn(false);
		when(passwordEncoder.encode("senha-forte-123")).thenReturn("hash");
		when(userRepository.save(any(User.class))).thenAnswer(inv -> {
			User u = inv.getArgument(0);
			ReflectionTestUtils.setField(u, "id", userId);
			return u;
		});
		when(jwtService.generateToken(any(User.class))).thenReturn("access-jwt");
		when(refreshTokenService.issue(userId)).thenReturn("refresh-raw");

		IssuedTokens tokens = authService.register(" Victor@Poupito.com ", "senha-forte-123");

		assertThat(tokens.accessToken()).isEqualTo("access-jwt");
		assertThat(tokens.refreshToken()).isEqualTo("refresh-raw");
		assertThat(tokens.email()).isEqualTo("victor@poupito.com");
		assertThat(tokens.user().email()).isEqualTo("victor@poupito.com");
	}

	@Test
	void shouldThrowEmailAlreadyUsed_whenEmailExists() {
		when(userRepository.existsByEmail("victor@poupito.com")).thenReturn(true);

		assertThatThrownBy(() -> authService.register("victor@poupito.com", "senha-forte-123"))
				.isInstanceOf(EmailAlreadyUsedException.class);
		verify(userRepository, never()).save(any());
	}

	@Test
	void shouldLoginAndReturnTokens_whenCredentialsAreValid() {
		User user = userWithId("victor@poupito.com");
		when(userRepository.findByEmail("victor@poupito.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("senha-forte-123", "hash")).thenReturn(true);
		when(jwtService.generateToken(user)).thenReturn("access-jwt");
		when(refreshTokenService.issue(userId)).thenReturn("refresh-raw");

		IssuedTokens tokens = authService.login("victor@poupito.com", "senha-forte-123");

		assertThat(tokens.accessToken()).isEqualTo("access-jwt");
		assertThat(tokens.userId()).isEqualTo(userId);
	}

	@Test
	void shouldThrowInvalidCredentials_whenEmailIsUnknown() {
		when(userRepository.findByEmail("nao-existe@poupito.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.login("nao-existe@poupito.com", "qualquer-123"))
				.isInstanceOf(InvalidCredentialsException.class);
	}

	@Test
	void shouldThrowInvalidCredentials_whenPasswordIsWrong() {
		User user = userWithId("victor@poupito.com");
		when(userRepository.findByEmail("victor@poupito.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("senha-errada", "hash")).thenReturn(false);

		assertThatThrownBy(() -> authService.login("victor@poupito.com", "senha-errada"))
				.isInstanceOf(InvalidCredentialsException.class);
	}

	@Test
	void shouldRotateAndIssueNewTokens_whenRefreshIsValid() {
		when(refreshTokenService.rotate("old-refresh"))
				.thenReturn(new RefreshTokenService.Rotation(userId, "new-refresh"));
		User user = userWithId("victor@poupito.com");
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(jwtService.generateToken(user)).thenReturn("new-access");

		IssuedTokens tokens = authService.refresh("old-refresh");

		assertThat(tokens.accessToken()).isEqualTo("new-access");
		assertThat(tokens.refreshToken()).isEqualTo("new-refresh");
	}

	@Test
	void shouldThrowInvalidRefresh_whenRefreshTokenIsBlank() {
		assertThatThrownBy(() -> authService.refresh("  "))
				.isInstanceOf(InvalidRefreshTokenException.class);
		verify(refreshTokenService, never()).rotate(any());
	}

	@Test
	void shouldThrowInvalidRefresh_whenUserNoLongerExists() {
		when(refreshTokenService.rotate("old-refresh"))
				.thenReturn(new RefreshTokenService.Rotation(userId, "new-refresh"));
		when(userRepository.findById(userId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.refresh("old-refresh"))
				.isInstanceOf(InvalidRefreshTokenException.class);
	}

	@Test
	void shouldRevokeToken_whenLoggingOut() {
		authService.logout("some-refresh");

		verify(refreshTokenService).revoke("some-refresh");
	}

	@Test
	void shouldIssueTokenAndSendEmail_whenForgotPasswordAndEmailExists() {
		User user = userWithId("victor@poupito.com");
		when(userRepository.findByEmail("victor@poupito.com")).thenReturn(Optional.of(user));
		when(passwordResetService.issue(userId)).thenReturn("reset-raw");

		authService.forgotPassword(" Victor@Poupito.com ");

		verify(passwordResetService).issue(userId);
		verify(passwordResetMailer).sendResetLink("victor@poupito.com", "reset-raw");
	}

	@Test
	void shouldDoNothingButNotFail_whenForgotPasswordAndEmailUnknown() {
		when(userRepository.findByEmail("nao-existe@poupito.com")).thenReturn(Optional.empty());

		authService.forgotPassword("nao-existe@poupito.com");

		verify(passwordResetService, never()).issue(any());
		verify(passwordResetMailer, never()).sendResetLink(any(), any());
	}

	@Test
	void shouldChangePasswordAndRevokeSessions_whenResetPasswordIsValid() {
		User user = userWithId("victor@poupito.com");
		when(passwordResetService.consume("reset-raw")).thenReturn(userId);
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(passwordEncoder.encode("NovaSenha-2026")).thenReturn("new-hash");

		authService.resetPassword("reset-raw", "NovaSenha-2026");

		assertThat(user.getPasswordHash()).isEqualTo("new-hash");
		verify(refreshTokenService).revokeAllForUser(userId);
	}

	@Test
	void shouldThrowBusiness_whenResetPasswordUserNoLongerExists() {
		when(passwordResetService.consume("reset-raw")).thenReturn(userId);
		when(userRepository.findById(userId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.resetPassword("reset-raw", "NovaSenha-2026"))
				.isInstanceOf(BusinessException.class);
		verify(refreshTokenService, never()).revokeAllForUser(any());
	}

}
