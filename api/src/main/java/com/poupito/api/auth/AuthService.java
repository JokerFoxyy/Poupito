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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final RefreshTokenService refreshTokenService;
	private final PasswordResetService passwordResetService;
	private final PasswordResetMailer passwordResetMailer;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
			JwtService jwtService, RefreshTokenService refreshTokenService,
			PasswordResetService passwordResetService, PasswordResetMailer passwordResetMailer) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.refreshTokenService = refreshTokenService;
		this.passwordResetService = passwordResetService;
		this.passwordResetMailer = passwordResetMailer;
	}

	@Transactional
	public IssuedTokens register(String email, String rawPassword) {
		String normalizedEmail = normalize(email);
		if (userRepository.existsByEmail(normalizedEmail)) {
			throw new EmailAlreadyUsedException();
		}
		User user = userRepository.save(new User(normalizedEmail, passwordEncoder.encode(rawPassword)));
		return issueFor(user);
	}

	@Transactional
	public IssuedTokens login(String email, String rawPassword) {
		User user = userRepository.findByEmail(normalize(email))
				.orElseThrow(InvalidCredentialsException::new);
		if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
			throw new InvalidCredentialsException();
		}
		return issueFor(user);
	}

	/** Rotaciona o refresh token e emite um novo par de tokens. */
	@Transactional
	public IssuedTokens refresh(String rawRefreshToken) {
		if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
			throw new InvalidRefreshTokenException();
		}
		RefreshTokenService.Rotation rotation = refreshTokenService.rotate(rawRefreshToken);
		User user = userRepository.findById(rotation.userId())
				.orElseThrow(InvalidRefreshTokenException::new);
		return new IssuedTokens(jwtService.generateToken(user), rotation.rawToken(),
				user.getId(), user.getEmail());
	}

	@Transactional
	public void logout(String rawRefreshToken) {
		refreshTokenService.revoke(rawRefreshToken);
	}

	/**
	 * Inicia a recuperação de senha. Sempre retorna normalmente (anti-enumeração — o controller
	 * responde 204 mesmo se o email não existir). Se existir, emite um token de reset e envia o email.
	 */
	@Transactional
	public void forgotPassword(String email) {
		userRepository.findByEmail(normalize(email)).ifPresent(user -> {
			String rawToken = passwordResetService.issue(user.getId());
			passwordResetMailer.sendResetLink(user.getEmail(), rawToken);
		});
	}

	/**
	 * Consome o token de reset, aplica a nova senha e revoga todas as sessões (refresh tokens) do
	 * usuário. Token inválido/expirado/já usado → 400.
	 */
	@Transactional
	public void resetPassword(String rawToken, String newPassword) {
		UUID userId = passwordResetService.consume(rawToken);
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException("Link de redefinição inválido ou expirado"));
		user.changePassword(passwordEncoder.encode(newPassword));
		refreshTokenService.revokeAllForUser(userId);
	}

	private IssuedTokens issueFor(User user) {
		String accessToken = jwtService.generateToken(user);
		String refreshToken = refreshTokenService.issue(user.getId());
		return new IssuedTokens(accessToken, refreshToken, user.getId(), user.getEmail());
	}

	private String normalize(String email) {
		return email.trim().toLowerCase();
	}

}
