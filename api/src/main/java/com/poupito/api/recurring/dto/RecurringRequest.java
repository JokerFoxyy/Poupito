package com.poupito.api.recurring.dto;

import com.poupito.api.transaction.TransactionType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Fixo em conta <b>XOR</b> cartão (sessão #32): informe {@code accountId} ou {@code cardId} — nunca
 * os dois, nunca nenhum. A checagem fica no service (mesma semântica do TransactionRequest da #25),
 * não em anotação, porque depende dos dois campos juntos.
 */
public record RecurringRequest(
		@NotBlank @Size(max = 200) String description,
		@NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal amount,
		@NotNull TransactionType type,
		UUID accountId,
		UUID cardId,
		@NotNull UUID categoryId,
		@NotNull @Min(1) @Max(31) Integer dayOfMonth,
		Boolean active,
		LocalDate endDate) {

	public boolean activeOrDefault() {
		return active == null || active;
	}

	public boolean onCard() {
		return cardId != null;
	}

	/** Exatamente um dos dois preenchido. */
	public boolean hasExactlyOneTarget() {
		return (accountId == null) != (cardId == null);
	}

}
