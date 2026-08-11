package com.poupito.api.card;

import com.poupito.api.account.Account;
import com.poupito.api.account.AccountRepository;
import com.poupito.api.account.AccountType;
import com.poupito.api.card.dto.CardRequest;
import com.poupito.api.card.dto.CardResponse;
import com.poupito.api.common.error.DuplicateResourceException;
import com.poupito.api.common.error.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardServiceTest {

	private final UUID userId = UUID.randomUUID();

	@Mock
	private CardRepository cardRepository;
	@Mock
	private AccountRepository accountRepository;

	@InjectMocks
	private CardService cardService;

	private Account account() {
		Account account = new Account(userId, "Nubank Conta", AccountType.CHECKING);
		ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
		return account;
	}

	@Test
	void shouldCreateCard_linkedToOwnedAccount() {
		Account account = account();
		when(accountRepository.findByIdAndUserId(account.getId(), userId)).thenReturn(Optional.of(account));
		when(cardRepository.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));

		CardResponse response = cardService.create(userId,
				new CardRequest(" Nubank ", account.getId(), 28, 7));

		assertThat(response.name()).isEqualTo("Nubank");
		assertThat(response.accountId()).isEqualTo(account.getId());
		assertThat(response.accountName()).isEqualTo("Nubank Conta");
		assertThat(response.closingDay()).isEqualTo(28);
		assertThat(response.dueDay()).isEqualTo(7);
	}

	@Test
	void shouldThrowDuplicate_whenCreatingCardWithExistingName() {
		Account account = account();
		when(accountRepository.findByIdAndUserId(account.getId(), userId)).thenReturn(Optional.of(account));
		when(cardRepository.existsByUserIdAndNameIgnoreCase(userId, "Nubank")).thenReturn(true);

		assertThatThrownBy(() -> cardService.create(userId,
				new CardRequest("Nubank", account.getId(), 28, 7)))
				.isInstanceOf(DuplicateResourceException.class);
		verify(cardRepository, never()).save(any());
	}

	@Test
	void shouldThrowDuplicate_whenRenamingCardOntoAnotherName() {
		Account account = account();
		Card card = new Card(userId, account.getId(), "Itau", 5, 15);
		when(cardRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.of(card));
		when(accountRepository.findByIdAndUserId(account.getId(), userId)).thenReturn(Optional.of(account));
		when(cardRepository.existsByUserIdAndNameIgnoreCase(userId, "Nubank")).thenReturn(true);

		assertThatThrownBy(() -> cardService.update(userId, UUID.randomUUID(),
				new CardRequest("Nubank", account.getId(), 5, 15)))
				.isInstanceOf(DuplicateResourceException.class);
		assertThat(card.getName()).isEqualTo("Itau");
	}

	@Test
	void shouldAllowSavingCardKeepingItsOwnName() {
		Account account = account();
		Card card = new Card(userId, account.getId(), "Nubank", 5, 15);
		when(cardRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.of(card));
		when(accountRepository.findByIdAndUserId(account.getId(), userId)).thenReturn(Optional.of(account));

		CardResponse response = cardService.update(userId, UUID.randomUUID(),
				new CardRequest("Nubank", account.getId(), 10, 20));

		verify(cardRepository, never()).existsByUserIdAndNameIgnoreCase(any(), any());
		assertThat(response.closingDay()).isEqualTo(10);
	}

	@Test
	void shouldThrowNotFound_whenLinkedAccountIsNotOwned() {
		when(accountRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> cardService.create(userId, new CardRequest("Nubank", UUID.randomUUID(), 28, 7)))
				.isInstanceOf(NotFoundException.class);
	}

	@Test
	void shouldUpdateCard_whenOwned() {
		Account account = account();
		Card card = new Card(userId, account.getId(), "Nubank", 28, 7);
		when(cardRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.of(card));
		when(accountRepository.findByIdAndUserId(account.getId(), userId)).thenReturn(Optional.of(account));

		CardResponse response = cardService.update(userId, UUID.randomUUID(),
				new CardRequest("Nubank Ultravioleta", account.getId(), 25, 4));

		assertThat(response.name()).isEqualTo("Nubank Ultravioleta");
		assertThat(response.closingDay()).isEqualTo(25);
	}

	@Test
	void shouldThrowNotFound_whenUpdatingCardOfAnotherUser() {
		when(cardRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> cardService.update(userId, UUID.randomUUID(),
				new CardRequest("Nubank", UUID.randomUUID(), 28, 7)))
				.isInstanceOf(NotFoundException.class);
	}

	@Test
	void shouldDeleteCard_whenOwned() {
		Card card = new Card(userId, UUID.randomUUID(), "Nubank", 28, 7);
		when(cardRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.of(card));

		cardService.delete(userId, UUID.randomUUID());

		verify(cardRepository).delete(card);
	}

	@Test
	void shouldListCardsWithAccountName() {
		Account account = account();
		Card card = new Card(userId, account.getId(), "Nubank", 28, 7);
		when(cardRepository.findAllByUserIdAndArchivedOrderByNameAsc(userId, false)).thenReturn(List.of(card));
		when(accountRepository.findAllByUserIdOrderByNameAsc(userId)).thenReturn(List.of(account));

		List<CardResponse> cards = cardService.list(userId, false);

		assertThat(cards).hasSize(1);
		assertThat(cards.getFirst().accountName()).isEqualTo("Nubank Conta");
	}

	@Test
	void shouldArchiveAndUnarchiveCard() {
		Account account = account();
		Card card = new Card(userId, account.getId(), "Nubank", 28, 7);
		UUID cardId = UUID.randomUUID();
		ReflectionTestUtils.setField(card, "id", cardId);
		when(cardRepository.findByIdAndUserId(cardId, userId)).thenReturn(Optional.of(card));
		when(accountRepository.findByIdAndUserId(account.getId(), userId)).thenReturn(Optional.of(account));

		CardResponse archived = cardService.archive(userId, cardId);
		assertThat(archived.archived()).isTrue();
		assertThat(card.isArchived()).isTrue();

		CardResponse unarchived = cardService.unarchive(userId, cardId);
		assertThat(unarchived.archived()).isFalse();
		assertThat(card.isArchived()).isFalse();
	}

	@Test
	void shouldThrowNotFound_whenArchivingCardOfAnotherUser() {
		UUID cardId = UUID.randomUUID();
		when(cardRepository.findByIdAndUserId(cardId, userId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> cardService.archive(userId, cardId)).isInstanceOf(NotFoundException.class);
	}

}
