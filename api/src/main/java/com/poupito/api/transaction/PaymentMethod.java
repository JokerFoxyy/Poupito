package com.poupito.api.transaction;

import com.poupito.api.account.AccountType;

import java.util.UUID;

/** Método de pagamento derivado — nunca digitado pelo usuário (sessão #25). */
public enum PaymentMethod {
	CREDITO,
	DEBITO,
	DINHEIRO;

	public static PaymentMethod of(Transaction transaction, AccountType accountType) {
		return of(transaction.getCardId(), accountType);
	}

	/**
	 * Regra base: cartão → crédito; conta CASH → dinheiro; senão débito. Recebe os campos soltos
	 * para servir também aos fixos (sessão #32), que têm o mesmo XOR conta/cartão mas não são
	 * {@link Transaction}.
	 */
	public static PaymentMethod of(UUID cardId, AccountType accountType) {
		if (cardId != null) {
			return CREDITO;
		}
		return accountType == AccountType.CASH ? DINHEIRO : DEBITO;
	}
}
