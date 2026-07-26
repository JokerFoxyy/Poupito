package com.poupito.api.card;

import com.poupito.api.account.Account;
import com.poupito.api.account.AccountRepository;
import com.poupito.api.card.dto.CardRequest;
import com.poupito.api.card.dto.CardResponse;
import com.poupito.api.common.error.DuplicateResourceException;
import com.poupito.api.common.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CardService {

	private final CardRepository cardRepository;
	private final AccountRepository accountRepository;

	public CardService(CardRepository cardRepository, AccountRepository accountRepository) {
		this.cardRepository = cardRepository;
		this.accountRepository = accountRepository;
	}

	@Transactional(readOnly = true)
	public List<CardResponse> list(UUID userId) {
		List<Card> cards = cardRepository.findAllByUserIdOrderByNameAsc(userId);
		Map<UUID, Account> accounts = accountRepository.findAllByUserIdOrderByNameAsc(userId).stream()
				.collect(Collectors.toMap(Account::getId, Function.identity()));
		return cards.stream()
				.map(card -> CardResponse.from(card, accounts.get(card.getAccountId())))
				.toList();
	}

	@Transactional
	public CardResponse create(UUID userId, CardRequest request) {
		Account account = ownedAccount(userId, request.accountId());
		String name = request.name().trim();
		requireNameAvailable(userId, name);
		Card card = new Card(userId, account.getId(), name, request.closingDay(), request.dueDay());
		return CardResponse.from(cardRepository.save(card), account);
	}

	@Transactional
	public CardResponse update(UUID userId, UUID cardId, CardRequest request) {
		Card card = findOwned(userId, cardId);
		Account account = ownedAccount(userId, request.accountId());
		String name = request.name().trim();
		// só checa se o nome realmente mudou, senão salvar sem renomear bateria no próprio cartão
		if (!card.getName().equalsIgnoreCase(name)) {
			requireNameAvailable(userId, name);
		}
		card.update(account.getId(), name, request.closingDay(), request.dueDay());
		return CardResponse.from(card, account);
	}

	/** Nome de cartão é único por usuário (sessão #34) — dois "Nubank" tornam os seletores ambíguos. */
	private void requireNameAvailable(UUID userId, String name) {
		if (cardRepository.existsByUserIdAndNameIgnoreCase(userId, name)) {
			throw new DuplicateResourceException("Você já tem um cartão com esse nome");
		}
	}

	/** Delete falha com 409 se houver transações/faturas no cartão (FK), igual a contas/categorias. */
	@Transactional
	public void delete(UUID userId, UUID cardId) {
		cardRepository.delete(findOwned(userId, cardId));
	}

	private Card findOwned(UUID userId, UUID cardId) {
		return cardRepository.findByIdAndUserId(cardId, userId)
				.orElseThrow(() -> new NotFoundException("Cartão não encontrado"));
	}

	private Account ownedAccount(UUID userId, UUID accountId) {
		return accountRepository.findByIdAndUserId(accountId, userId)
				.orElseThrow(() -> new NotFoundException("Conta não encontrada"));
	}

}
