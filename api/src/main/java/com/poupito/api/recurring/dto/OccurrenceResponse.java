package com.poupito.api.recurring.dto;

import com.poupito.api.transaction.PaymentMethod;
import com.poupito.api.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Estado de um fixo num mês específico: se já foi materializado e se está pago.
 * Fixo no cartão (sessão #32) nasce pago — a quitação é o pagamento da fatura ({@code INVOICE_PAYMENT}),
 * não um checkbox por ocorrência; o front usa {@code method}/{@code cardName} para mostrar isso.
 */
public record OccurrenceResponse(
		UUID recurringId,
		String description,
		BigDecimal amount,
		TransactionType type,
		String accountName,
		String cardName,
		PaymentMethod method,
		String categoryName,
		String categoryIcon,
		String categoryColor,
		int dayOfMonth,
		LocalDate date,
		UUID transactionId,
		boolean materialized,
		boolean paid) {
}
