package com.poupito.api.recurring.dto;

import com.poupito.api.account.Account;
import com.poupito.api.card.Card;
import com.poupito.api.category.Category;
import com.poupito.api.recurring.RecurringTransaction;
import com.poupito.api.transaction.PaymentMethod;
import com.poupito.api.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RecurringResponse(
		UUID id,
		String description,
		BigDecimal amount,
		TransactionType type,
		UUID accountId,
		String accountName,
		UUID cardId,
		String cardName,
		PaymentMethod method,
		UUID categoryId,
		String categoryName,
		String categoryIcon,
		String categoryColor,
		int dayOfMonth,
		boolean active,
		LocalDate endDate) {

	public static RecurringResponse from(RecurringTransaction recurring, Account account, Card card,
			Category category) {
		return new RecurringResponse(
				recurring.getId(),
				recurring.getDescription(),
				recurring.getAmount(),
				recurring.getType(),
				recurring.getAccountId(),
				account != null ? account.getName() : null,
				recurring.getCardId(),
				card != null ? card.getName() : null,
				PaymentMethod.of(recurring.getCardId(), account != null ? account.getType() : null),
				recurring.getCategoryId(),
				category != null ? category.getName() : null,
				category != null ? category.getIcon() : null,
				category != null ? category.getColor() : null,
				recurring.getDayOfMonth(),
				recurring.isActive(),
				recurring.getEndDate());
	}

}
