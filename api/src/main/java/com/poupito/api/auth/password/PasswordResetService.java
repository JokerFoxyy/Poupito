package com.poupito.api.auth.password;

import com.poupito.api.common.error.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Emite e consome tokens de redefinição de senha. Mesmo padrão do refresh token (#S):
 * o token em claro (256 bits) só existe no email enviado; o banco guarda o hash SHA-256.
 * Single-use e com expiração curta.
 */
@Service
public class PasswordResetService {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final Base64.Encoder BASE64 = Base64.getUrlEncoder().withoutPadding();

	private final PasswordResetTokenRepository repository;
	private final Duration ttl;

	public PasswordResetService(PasswordResetTokenRepository repository,
			@Value("${app.password-reset.ttl}") Duration ttl) {
		this.repository = repository;
		this.ttl = ttl;
	}

	/**
	 * Invalida tokens ativos anteriores do usuário e emite um novo, devolvendo o valor em claro
	 * (só ele; o banco guarda o hash).
	 */
	@Transactional
	public String issue(UUID userId) {
		repository.deleteByUserIdAndUsedAtIsNull(userId);
		String raw = randomToken();
		repository.save(new PasswordResetToken(userId, hash(raw), Instant.now().plus(ttl)));
		return raw;
	}

	/**
	 * Valida o token (existe, não expirado, não usado), marca-o como usado e devolve o id do
	 * usuário. Token inválido/expirado/já usado → {@link BusinessException} (400).
	 */
	@Transactional
	public UUID consume(String rawToken) {
		if (rawToken == null || rawToken.isBlank()) {
			throw new BusinessException("Link de redefinição inválido ou expirado");
		}
		PasswordResetToken token = repository.findByTokenHash(hash(rawToken))
				.orElseThrow(() -> new BusinessException("Link de redefinição inválido ou expirado"));
		if (!token.isUsable(Instant.now())) {
			throw new BusinessException("Link de redefinição inválido ou expirado");
		}
		token.markUsed(Instant.now());
		return token.getUserId();
	}

	private String randomToken() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return BASE64.encodeToString(bytes);
	}

	private String hash(String raw) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception e) {
			throw new IllegalStateException("SHA-256 indisponível", e);
		}
	}

}
