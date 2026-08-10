package com.poupito.api.card.dto;

import com.poupito.api.account.Account;
import com.poupito.api.card.Card;

import java.util.UUID;

public record CardResponse(
		UUID id, String name, UUID accountId, String accountName, Integer closingDay, Integer dueDay,
		boolean archived) {

	public static CardResponse from(Card card, Account account) {
		return new CardResponse(card.getId(), card.getName(), card.getAccountId(),
				account != null ? account.getName() : null, card.getClosingDay(), card.getDueDay(),
				card.isArchived());
	}

}
