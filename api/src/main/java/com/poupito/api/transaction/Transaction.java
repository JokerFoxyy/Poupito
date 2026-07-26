package com.poupito.api.transaction;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class Transaction {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	/** Conta (débito/dinheiro) — nula quando o lançamento é no cartão (xor com cardId). */
	@Column(name = "account_id")
	private UUID accountId;

	/** Cartão de crédito — nulo quando o lançamento é direto na conta (xor com accountId). */
	@Column(name = "card_id")
	private UUID cardId;

	@Column(name = "category_id")
	private UUID categoryId;

	@Column(name = "invoice_id")
	private UUID invoiceId;

	@Column(nullable = false)
	private String description;

	@Column(nullable = false)
	private BigDecimal amount;

	@Column(nullable = false)
	private LocalDate date;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TransactionType type;

	@Column(name = "recurring_id")
	private UUID recurringId;

	@Column(nullable = false)
	private boolean paid = true;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "installment_group_id")
	private UUID installmentGroupId;

	@Column(name = "installment_number")
	private Integer installmentNumber;

	@Column(name = "installment_count")
	private Integer installmentCount;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "transaction_tags", joinColumns = @JoinColumn(name = "transaction_id"))
	@Column(name = "tag")
	private Set<String> tags = new HashSet<>();

	protected Transaction() {
	}

	private Transaction(UUID userId, UUID accountId, UUID cardId, UUID categoryId, UUID invoiceId,
			String description, BigDecimal amount, LocalDate date, TransactionType type) {
		this.userId = userId;
		this.accountId = accountId;
		this.cardId = cardId;
		this.categoryId = categoryId;
		this.invoiceId = invoiceId;
		this.description = description;
		this.amount = amount;
		this.date = date;
		this.type = type;
	}

	/** Lançamento direto na conta (débito/dinheiro). */
	public static Transaction forAccount(UUID userId, UUID accountId, UUID categoryId,
			String description, BigDecimal amount, LocalDate date, TransactionType type) {
		return new Transaction(userId, accountId, null, categoryId, null, description, amount, date, type);
	}

	/** Lançamento no cartão de crédito, vinculado à fatura do período. */
	public static Transaction forCard(UUID userId, UUID cardId, UUID categoryId, UUID invoiceId,
			String description, BigDecimal amount, LocalDate date, TransactionType type) {
		return new Transaction(userId, null, cardId, categoryId, invoiceId, description, amount, date, type);
	}

	/** Transação gerada por um fixo em conta: vinculada ao recurring e nasce não-paga (checkbox "pago?"). */
	public static Transaction materialized(UUID userId, UUID accountId, UUID categoryId,
			String description, BigDecimal amount, LocalDate date, TransactionType type, UUID recurringId) {
		Transaction transaction = forAccount(userId, accountId, categoryId, description, amount, date, type);
		transaction.recurringId = recurringId;
		transaction.paid = false;
		return transaction;
	}

	/**
	 * Transação gerada por um fixo no cartão de crédito (sessão #32): entra na fatura do período e
	 * nasce <b>paga</b> — a quitação é o pagamento da fatura ({@code INVOICE_PAYMENT}), não um
	 * checkbox por ocorrência.
	 */
	public static Transaction materializedOnCard(UUID userId, UUID cardId, UUID categoryId, UUID invoiceId,
			String description, BigDecimal amount, LocalDate date, TransactionType type, UUID recurringId) {
		Transaction transaction = forCard(userId, cardId, categoryId, invoiceId, description, amount, date, type);
		transaction.recurringId = recurringId;
		transaction.paid = true;
		return transaction;
	}

	/** Uma parcela (1..count) de uma compra parcelada no cartão; amount é o valor da própria parcela. */
	public static Transaction installment(UUID userId, UUID cardId, UUID categoryId, UUID invoiceId,
			String description, BigDecimal amount, LocalDate date, TransactionType type,
			UUID installmentGroupId, int installmentNumber, int installmentCount) {
		Transaction transaction = forCard(userId, cardId, categoryId, invoiceId,
				description, amount, date, type);
		transaction.installmentGroupId = installmentGroupId;
		transaction.installmentNumber = installmentNumber;
		transaction.installmentCount = installmentCount;
		return transaction;
	}

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	public void update(UUID accountId, UUID cardId, UUID categoryId, UUID invoiceId,
			String description, BigDecimal amount, LocalDate date, TransactionType type) {
		this.accountId = accountId;
		this.cardId = cardId;
		this.categoryId = categoryId;
		this.invoiceId = invoiceId;
		this.description = description;
		this.amount = amount;
		this.date = date;
		this.type = type;
	}

	public void updateTags(Set<String> tags) {
		this.tags.clear();
		this.tags.addAll(tags);
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

	public UUID getCategoryId() {
		return categoryId;
	}

	public UUID getInvoiceId() {
		return invoiceId;
	}

	public String getDescription() {
		return description;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public LocalDate getDate() {
		return date;
	}

	public TransactionType getType() {
		return type;
	}

	public UUID getRecurringId() {
		return recurringId;
	}

	public boolean isPaid() {
		return paid;
	}

	public void markPaid(boolean paid) {
		this.paid = paid;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Set<String> getTags() {
		return tags;
	}

	public UUID getInstallmentGroupId() {
		return installmentGroupId;
	}

	public Integer getInstallmentNumber() {
		return installmentNumber;
	}

	public Integer getInstallmentCount() {
		return installmentCount;
	}

}
