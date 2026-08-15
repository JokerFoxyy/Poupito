package com.poupito.api.recurring;

import com.poupito.api.account.Account;
import com.poupito.api.account.AccountRepository;
import com.poupito.api.account.AccountType;
import com.poupito.api.card.Card;
import com.poupito.api.card.CardRepository;
import com.poupito.api.category.Category;
import com.poupito.api.category.CategoryKind;
import com.poupito.api.category.CategoryRepository;
import com.poupito.api.common.error.BusinessException;
import com.poupito.api.common.error.NotFoundException;
import com.poupito.api.recurring.dto.RecurringRequest;
import com.poupito.api.recurring.dto.RecurringResponse;
import com.poupito.api.transaction.PaymentMethod;
import com.poupito.api.transaction.TransactionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
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
class RecurringServiceTest {

	private final UUID userId = UUID.randomUUID();

	@Mock
	private RecurringTransactionRepository recurringRepository;
	@Mock
	private AccountRepository accountRepository;
	@Mock
	private CardRepository cardRepository;
	@Mock
	private CategoryRepository categoryRepository;

	@InjectMocks
	private RecurringService service;

	private Account account() {
		Account account = new Account(userId, "Uniclass", AccountType.CHECKING);
		ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
		return account;
	}

	private Card card(Account account) {
		Card card = new Card(userId, account.getId(), "Nubank", 3, 10);
		ReflectionTestUtils.setField(card, "id", UUID.randomUUID());
		return card;
	}

	private Category category(CategoryKind kind) {
		Category category = new Category(userId, "Assinaturas", "🔁", "#a371f7", kind);
		ReflectionTestUtils.setField(category, "id", UUID.randomUUID());
		return category;
	}

	private RecurringRequest onAccount(Account account, Category category, TransactionType type) {
		return new RecurringRequest("Spotify", new BigDecimal("27.90"), type,
				account.getId(), null, category.getId(), 10, true, null);
	}

	private RecurringRequest onCard(Card card, Category category, TransactionType type) {
		return new RecurringRequest("Netflix", new BigDecimal("55.90"), type,
				null, card.getId(), category.getId(), 10, true, null);
	}

	@Test
	void shouldCreateRecurring_whenOnAccount() {
		Account account = account();
		Category category = category(CategoryKind.EXPENSE);
		when(accountRepository.findByIdAndUserId(account.getId(), userId)).thenReturn(Optional.of(account));
		when(categoryRepository.findByIdAndUserId(category.getId(), userId)).thenReturn(Optional.of(category));
		when(recurringRepository.save(any(RecurringTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

		RecurringResponse response = service.create(userId, onAccount(account, category, TransactionType.EXPENSE));

		assertThat(response.description()).isEqualTo("Spotify");
		assertThat(response.accountId()).isEqualTo(account.getId());
		assertThat(response.cardId()).isNull();
		assertThat(response.method()).isEqualTo(PaymentMethod.DEBITO);
	}

	@Test
	void shouldCreateRecurring_whenOnCard() {
		Account account = account();
		Card card = card(account);
		Category category = category(CategoryKind.EXPENSE);
		when(cardRepository.findByIdAndUserId(card.getId(), userId)).thenReturn(Optional.of(card));
		when(categoryRepository.findByIdAndUserId(category.getId(), userId)).thenReturn(Optional.of(category));
		when(recurringRepository.save(any(RecurringTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

		RecurringResponse response = service.create(userId, onCard(card, category, TransactionType.EXPENSE));

		assertThat(response.description()).isEqualTo("Netflix");
		assertThat(response.cardId()).isEqualTo(card.getId());
		assertThat(response.cardName()).isEqualTo("Nubank");
		assertThat(response.accountId()).isNull();
		assertThat(response.method()).isEqualTo(PaymentMethod.CREDITO);
	}

	@Test
	void shouldThrowBusiness_whenNeitherAccountNorCardIsGiven() {
		Category category = category(CategoryKind.EXPENSE);

		assertThatThrownBy(() -> service.create(userId, new RecurringRequest("x", BigDecimal.TEN,
				TransactionType.EXPENSE, null, null, category.getId(), 10, true, null)))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("conta OU cartão");
		verify(recurringRepository, never()).save(any());
	}

	@Test
	void shouldThrowBusiness_whenBothAccountAndCardAreGiven() {
		assertThatThrownBy(() -> service.create(userId, new RecurringRequest("x", BigDecimal.TEN,
				TransactionType.EXPENSE, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 10, true, null)))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("conta OU cartão");
		verify(recurringRepository, never()).save(any());
	}

	@Test
	void shouldThrowBusiness_whenIncomeIsOnCard() {
		Card card = card(account());
		Category income = category(CategoryKind.INCOME);

		assertThatThrownBy(() -> service.create(userId, onCard(card, income, TransactionType.INCOME)))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("cartão de crédito");
		verify(recurringRepository, never()).save(any());
	}

	@Test
	void shouldThrowBusiness_whenTypeIsInvoiceAdjustment() {
		assertThatThrownBy(() -> service.create(userId, new RecurringRequest("x", BigDecimal.TEN,
				TransactionType.INVOICE_ADJUSTMENT, UUID.randomUUID(), null, UUID.randomUUID(), 10, true, null)))
				.isInstanceOf(BusinessException.class);
		verify(recurringRepository, never()).save(any());
	}

	@Test
	void shouldThrowBusiness_whenTypeIsInvoicePayment() {
		assertThatThrownBy(() -> service.create(userId, new RecurringRequest("x", BigDecimal.TEN,
				TransactionType.INVOICE_PAYMENT, UUID.randomUUID(), null, UUID.randomUUID(), 10, true, null)))
				.isInstanceOf(BusinessException.class);
		verify(recurringRepository, never()).save(any());
	}

	@Test
	void shouldThrowBusiness_whenCategoryKindDoesNotMatchType() {
		Account account = account();
		Category income = category(CategoryKind.INCOME);
		when(categoryRepository.findByIdAndUserId(income.getId(), userId)).thenReturn(Optional.of(income));

		assertThatThrownBy(() -> service.create(userId, onAccount(account, income, TransactionType.EXPENSE)))
				.isInstanceOf(BusinessException.class);
	}

	@Test
	void shouldThrowNotFound_whenAccountBelongsToAnotherUser() {
		Category category = category(CategoryKind.EXPENSE);
		when(categoryRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.of(category));
		when(accountRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.create(userId, new RecurringRequest("x", BigDecimal.TEN,
				TransactionType.EXPENSE, UUID.randomUUID(), null, category.getId(), 10, true, null)))
				.isInstanceOf(NotFoundException.class)
				.hasMessageContaining("Conta");
	}

	@Test
	void shouldThrowNotFound_whenCardBelongsToAnotherUser() {
		Category category = category(CategoryKind.EXPENSE);
		when(categoryRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.of(category));
		when(cardRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.create(userId, new RecurringRequest("x", BigDecimal.TEN,
				TransactionType.EXPENSE, null, UUID.randomUUID(), category.getId(), 10, true, null)))
				.isInstanceOf(NotFoundException.class)
				.hasMessageContaining("Cartão");
	}

	@Test
	void shouldUpdateRecurring_whenOwned() {
		Account account = account();
		Category category = category(CategoryKind.EXPENSE);
		RecurringTransaction recurring = new RecurringTransaction(userId, account.getId(), null,
				category.getId(), "Spotify", new BigDecimal("27.90"), TransactionType.EXPENSE, 10, true, null);
		when(recurringRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.of(recurring));
		when(accountRepository.findByIdAndUserId(account.getId(), userId)).thenReturn(Optional.of(account));
		when(categoryRepository.findByIdAndUserId(category.getId(), userId)).thenReturn(Optional.of(category));

		RecurringResponse response = service.update(userId, UUID.randomUUID(),
				new RecurringRequest("Spotify Family", new BigDecimal("34.90"), TransactionType.EXPENSE,
						account.getId(), null, category.getId(), 15, false, null));

		assertThat(response.description()).isEqualTo("Spotify Family");
		assertThat(response.dayOfMonth()).isEqualTo(15);
		assertThat(response.active()).isFalse();
	}

	@Test
	void shouldMoveRecurringFromAccountToCard_whenUpdating() {
		Account account = account();
		Card card = card(account);
		Category category = category(CategoryKind.EXPENSE);
		RecurringTransaction recurring = new RecurringTransaction(userId, account.getId(), null,
				category.getId(), "Netflix", new BigDecimal("55.90"), TransactionType.EXPENSE, 10, true, null);
		when(recurringRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.of(recurring));
		when(cardRepository.findByIdAndUserId(card.getId(), userId)).thenReturn(Optional.of(card));
		when(categoryRepository.findByIdAndUserId(category.getId(), userId)).thenReturn(Optional.of(category));

		RecurringResponse response = service.update(userId, UUID.randomUUID(),
				onCard(card, category, TransactionType.EXPENSE));

		assertThat(recurring.getAccountId()).isNull();
		assertThat(recurring.getCardId()).isEqualTo(card.getId());
		assertThat(recurring.isOnCard()).isTrue();
		assertThat(response.method()).isEqualTo(PaymentMethod.CREDITO);
	}

	@Test
	void shouldThrowNotFound_whenUpdatingRecurringOfAnotherUser() {
		when(recurringRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.update(userId, UUID.randomUUID(), new RecurringRequest("x",
				BigDecimal.TEN, TransactionType.EXPENSE, UUID.randomUUID(), null, UUID.randomUUID(), 10, true, null)))
				.isInstanceOf(NotFoundException.class);
	}

	@Test
	void shouldDeleteRecurring_whenOwned() {
		RecurringTransaction recurring = new RecurringTransaction(userId, UUID.randomUUID(), null,
				UUID.randomUUID(), "Spotify", new BigDecimal("27.90"), TransactionType.EXPENSE, 10, true, null);
		when(recurringRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.of(recurring));

		service.delete(userId, UUID.randomUUID());

		verify(recurringRepository).delete(recurring);
	}

	@Test
	void shouldListRecurringOfUser_resolvingAccountAndCardNames() {
		Account account = account();
		Card card = card(account);
		Category category = category(CategoryKind.EXPENSE);
		RecurringTransaction onAccount = new RecurringTransaction(userId, account.getId(), null,
				category.getId(), "Academia", new BigDecimal("89.90"), TransactionType.EXPENSE, 5, true, null);
		RecurringTransaction onCard = new RecurringTransaction(userId, null, card.getId(),
				category.getId(), "Netflix", new BigDecimal("55.90"), TransactionType.EXPENSE, 5, true, null);
		when(recurringRepository.findAllByUserIdAndArchivedOrderByDescriptionAsc(userId, false))
				.thenReturn(List.of(onAccount, onCard));
		when(accountRepository.findByIdAndUserId(account.getId(), userId)).thenReturn(Optional.of(account));
		when(cardRepository.findByIdAndUserId(card.getId(), userId)).thenReturn(Optional.of(card));
		when(categoryRepository.findByIdAndUserId(category.getId(), userId)).thenReturn(Optional.of(category));

		List<RecurringResponse> list = service.list(userId, false);

		assertThat(list).hasSize(2);
		assertThat(list.getFirst().accountName()).isEqualTo("Uniclass");
		assertThat(list.getFirst().method()).isEqualTo(PaymentMethod.DEBITO);
		assertThat(list.getLast().cardName()).isEqualTo("Nubank");
		assertThat(list.getLast().method()).isEqualTo(PaymentMethod.CREDITO);
	}

	@Test
	void shouldArchiveAndUnarchiveRecurring() {
		Account account = account();
		Category category = category(CategoryKind.EXPENSE);
		RecurringTransaction recurring = new RecurringTransaction(userId, account.getId(), null,
				category.getId(), "Academia", new BigDecimal("89.90"), TransactionType.EXPENSE, 5, true, null);
		UUID recurringId = UUID.randomUUID();
		ReflectionTestUtils.setField(recurring, "id", recurringId);
		when(recurringRepository.findByIdAndUserId(recurringId, userId)).thenReturn(Optional.of(recurring));
		when(accountRepository.findByIdAndUserId(account.getId(), userId)).thenReturn(Optional.of(account));
		when(categoryRepository.findByIdAndUserId(category.getId(), userId)).thenReturn(Optional.of(category));

		RecurringResponse archived = service.archive(userId, recurringId);
		assertThat(archived.archived()).isTrue();
		assertThat(recurring.isArchived()).isTrue();

		RecurringResponse unarchived = service.unarchive(userId, recurringId);
		assertThat(unarchived.archived()).isFalse();
		assertThat(recurring.isArchived()).isFalse();
	}

	@Test
	void shouldThrowNotFound_whenArchivingRecurringOfAnotherUser() {
		UUID recurringId = UUID.randomUUID();
		when(recurringRepository.findByIdAndUserId(recurringId, userId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.archive(userId, recurringId)).isInstanceOf(NotFoundException.class);
	}

}
