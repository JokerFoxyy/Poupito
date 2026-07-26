package com.poupito.api.auth.password;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

	Optional<PasswordResetToken> findByTokenHash(String tokenHash);

	/** Invalida (remove) tokens de reset ainda não usados de um usuário ao emitir um novo. */
	void deleteByUserIdAndUsedAtIsNull(UUID userId);

}
