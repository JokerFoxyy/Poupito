package com.poupito.api.auth.password;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	/** SHA-256 (hex) do token opaco — o valor em claro nunca é persistido. */
	@Column(name = "token_hash", nullable = false, unique = true)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	/** Preenchido quando o token é consumido (single-use). Nulo enquanto válido. */
	@Column(name = "used_at")
	private Instant usedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected PasswordResetToken() {
	}

	public PasswordResetToken(UUID userId, String tokenHash, Instant expiresAt) {
		this.userId = userId;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
	}

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	public boolean isUsable(Instant now) {
		return usedAt == null && expiresAt.isAfter(now);
	}

	public void markUsed(Instant now) {
		this.usedAt = now;
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getUsedAt() {
		return usedAt;
	}

}
