package com.poupito.api.card;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Cartão de crédito: instrumento de pagamento vinculado a uma conta (que paga a fatura).
 * Não é uma conta — o dinheiro "vive" na conta vinculada (sessão #25).
 */
@Entity
@Table(name = "cards")
public class Card {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "account_id", nullable = false)
	private UUID accountId;

	@Column(nullable = false)
	private String name;

	@Column(name = "closing_day", nullable = false)
	private Integer closingDay;

	@Column(name = "due_day", nullable = false)
	private Integer dueDay;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/** Arquivado some da tela principal e dos seletores "Pagar com", mas o histórico é preservado (sessão #42). */
	@Column(nullable = false)
	private boolean archived = false;

	protected Card() {
	}

	public Card(UUID userId, UUID accountId, String name, Integer closingDay, Integer dueDay) {
		this.userId = userId;
		this.accountId = accountId;
		this.name = name;
		this.closingDay = closingDay;
		this.dueDay = dueDay;
	}

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	public void update(UUID accountId, String name, Integer closingDay, Integer dueDay) {
		this.accountId = accountId;
		this.name = name;
		this.closingDay = closingDay;
		this.dueDay = dueDay;
	}

	public void archive() {
		this.archived = true;
	}

	public void unarchive() {
		this.archived = false;
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public UUID getAccountId() {
		return accountId;
	}

	public String getName() {
		return name;
	}

	public Integer getClosingDay() {
		return closingDay;
	}

	public Integer getDueDay() {
		return dueDay;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public boolean isArchived() {
		return archived;
	}

}
