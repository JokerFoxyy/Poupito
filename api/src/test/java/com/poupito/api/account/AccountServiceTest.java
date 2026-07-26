package com.poupito.api.account;

import com.poupito.api.account.dto.AccountRequest;
import com.poupito.api.account.dto.AccountResponse;
import com.poupito.api.common.error.DuplicateResourceException;
import com.poupito.api.common.error.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class AccountServiceTest {

	private final UUID userId = UUID.randomUUID();

	@Mock
	private AccountRepository accountRepository;

	@InjectMocks
	private AccountService accountService;

	@Test
	void shouldCreateCheckingAccount_whenRequestIsValid() {
		when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

		AccountResponse response = accountService.create(userId,
				new AccountRequest(" Uniclass ", AccountType.CHECKING));

		assertThat(response.name()).isEqualTo("Uniclass");
		assertThat(response.type()).isEqualTo(AccountType.CHECKING);
	}

	@Test
	void shouldCreateCashAccount_whenRequestIsValid() {
		when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

		AccountResponse response = accountService.create(userId,
				new AccountRequest("Carteira", AccountType.CASH));

		assertThat(response.type()).isEqualTo(AccountType.CASH);
		ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
		verify(accountRepository).save(saved.capture());
		assertThat(saved.getValue().getUserId()).isEqualTo(userId);
	}

	@Test
	void shouldThrowDuplicate_whenCreatingAccountWithExistingName() {
		when(accountRepository.existsByUserIdAndNameIgnoreCase(userId, "Nubank")).thenReturn(true);

		assertThatThrownBy(() -> accountService.create(userId,
				new AccountRequest("Nubank", AccountType.CHECKING)))
				.isInstanceOf(DuplicateResourceException.class);
		verify(accountRepository, never()).save(any());
	}

	@Test
	void shouldThrowDuplicate_whenCreatingAccountDifferingOnlyByCase() {
		when(accountRepository.existsByUserIdAndNameIgnoreCase(userId, "nubank")).thenReturn(true);

		assertThatThrownBy(() -> accountService.create(userId,
				new AccountRequest(" nubank ", AccountType.CHECKING)))
				.isInstanceOf(DuplicateResourceException.class);
	}

	@Test
	void shouldThrowDuplicate_whenRenamingAccountOntoAnotherName() {
		Account account = new Account(userId, "Itau", AccountType.CHECKING);
		when(accountRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.of(account));
		when(accountRepository.existsByUserIdAndNameIgnoreCase(userId, "Nubank")).thenReturn(true);

		assertThatThrownBy(() -> accountService.update(userId, UUID.randomUUID(),
				new AccountRequest("Nubank", AccountType.CHECKING)))
				.isInstanceOf(DuplicateResourceException.class);
		assertThat(account.getName()).isEqualTo("Itau");
	}

	@Test
	void shouldAllowSavingAccountKeepingItsOwnName() {
		Account account = new Account(userId, "Nubank", AccountType.CHECKING);
		when(accountRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.of(account));

		AccountResponse response = accountService.update(userId, UUID.randomUUID(),
				new AccountRequest("Nubank", AccountType.CASH));

		// o nome não mudou, então a checagem de duplicata não roda (senão bateria na própria conta)
		verify(accountRepository, never()).existsByUserIdAndNameIgnoreCase(any(), any());
		assertThat(response.type()).isEqualTo(AccountType.CASH);
	}

	@Test
	void shouldUpdateAccount_whenOwnedByUser() {
		Account account = new Account(userId, "Nubank", AccountType.CHECKING);
		when(accountRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.of(account));

		AccountResponse response = accountService.update(userId, UUID.randomUUID(),
				new AccountRequest("Nubank Conta", AccountType.CHECKING));

		assertThat(response.name()).isEqualTo("Nubank Conta");
	}

	@Test
	void shouldThrowNotFound_whenUpdatingAccountOfAnotherUser() {
		when(accountRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> accountService.update(userId, UUID.randomUUID(),
				new AccountRequest("Conta", AccountType.CHECKING)))
				.isInstanceOf(NotFoundException.class);
	}

	@Test
	void shouldDeleteAccount_whenOwnedByUser() {
		Account account = new Account(userId, "Carteira", AccountType.CASH);
		when(accountRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.of(account));

		accountService.delete(userId, UUID.randomUUID());

		verify(accountRepository).delete(account);
	}

	@Test
	void shouldListAccountsOfUser_whenCalled() {
		when(accountRepository.findAllByUserIdOrderByNameAsc(userId))
				.thenReturn(List.of(new Account(userId, "Uniclass", AccountType.CHECKING)));

		List<AccountResponse> accounts = accountService.list(userId);

		assertThat(accounts).hasSize(1);
		assertThat(accounts.getFirst().name()).isEqualTo("Uniclass");
	}

}
