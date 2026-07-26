package com.poupito.api.recurring;

import com.poupito.api.transaction.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "recurring_transactions")
public class RecurringTransaction {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	/** Conta XOR cartão (CHECK no banco): exatamente um dos dois é preenchido — sessão #32. */
	@Column(name = "account_id")
	private UUID accountId;

	@Column(name = "card_id")
	private UUID cardId;

	@Column(name = "category_id", nullable = false)
	private UUID categoryId;

	@Column(nullable = false)
	private String description;

	@Column(nullable = false)
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TransactionType type;

	@Column(name = "day_of_month", nullable = false)
	private int dayOfMonth;

	@Column(nullable = false)
	private boolean active = true;

	@Column(name = "end_date")
	private LocalDate endDate;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected RecurringTransaction() {
	}

	public RecurringTransaction(UUID userId, UUID accountId, UUID cardId, UUID categoryId,
			String description, BigDecimal amount, TransactionType type, int dayOfMonth,
			boolean active, LocalDate endDate) {
		this.userId = userId;
		this.accountId = accountId;
		this.cardId = cardId;
		this.categoryId = categoryId;
		this.description = description;
		this.amount = amount;
		this.type = type;
		this.dayOfMonth = dayOfMonth;
		this.active = active;
		this.endDate = endDate;
	}

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	public void update(UUID accountId, UUID cardId, UUID categoryId, String description,
			BigDecimal amount, TransactionType type, int dayOfMonth, boolean active, LocalDate endDate) {
		this.accountId = accountId;
		this.cardId = cardId;
		this.categoryId = categoryId;
		this.description = description;
		this.amount = amount;
		this.type = type;
		this.dayOfMonth = dayOfMonth;
		this.active = active;
		this.endDate = endDate;
	}

	/** Data da ocorrência num dado mês (dia clampado ao fim do mês). */
	public LocalDate occurrenceDate(java.time.YearMonth month) {
		return month.atDay(Math.min(dayOfMonth, month.lengthOfMonth()));
	}

	/** O fixo gera ocorrência neste mês? (ativo e não encerrado antes da data da ocorrência) */
	public boolean occursIn(java.time.YearMonth month) {
		if (!active) {
			return false;
		}
		return endDate == null || !endDate.isBefore(occurrenceDate(month));
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

	public UUID getCardId() {
		return cardId;
	}

	/** Fixo cobrado no cartão de crédito (gera lançamento na fatura) — sessão #32. */
	public boolean isOnCard() {
		return cardId != null;
	}

	public UUID getCategoryId() {
		return categoryId;
	}

	public String getDescription() {
		return description;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public TransactionType getType() {
		return type;
	}

	public int getDayOfMonth() {
		return dayOfMonth;
	}

	public boolean isActive() {
		return active;
	}

	public LocalDate getEndDate() {
		return endDate;
	}

}
