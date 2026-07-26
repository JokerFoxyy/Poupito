package com.poupito.api.account;

import com.poupito.api.account.dto.AccountRequest;
import com.poupito.api.account.dto.AccountResponse;
import com.poupito.api.common.error.DuplicateResourceException;
import com.poupito.api.common.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AccountService {

	private final AccountRepository accountRepository;

	public AccountService(AccountRepository accountRepository) {
		this.accountRepository = accountRepository;
	}

	@Transactional(readOnly = true)
	public List<AccountResponse> list(UUID userId) {
		return accountRepository.findAllByUserIdOrderByNameAsc(userId).stream()
				.map(AccountResponse::from)
				.toList();
	}

	@Transactional
	public AccountResponse create(UUID userId, AccountRequest request) {
		String name = request.name().trim();
		requireNameAvailable(userId, name);
		Account account = new Account(userId, name, request.type());
		return AccountResponse.from(accountRepository.save(account));
	}

	@Transactional
	public AccountResponse update(UUID userId, UUID accountId, AccountRequest request) {
		Account account = findOwned(userId, accountId);
		String name = request.name().trim();
		// só checa se o nome realmente mudou, senão salvar sem renomear bateria na própria conta
		if (!account.getName().equalsIgnoreCase(name)) {
			requireNameAvailable(userId, name);
		}
		account.update(name, request.type());
		return AccountResponse.from(account);
	}

	/**
	 * Nome de conta é único por usuário (sessão #34): duas contas com o mesmo nome deixam os
	 * seletores ("Pagar com", filtros) ambíguos. Case-insensitive, como em categorias.
	 */
	private void requireNameAvailable(UUID userId, String name) {
		if (accountRepository.existsByUserIdAndNameIgnoreCase(userId, name)) {
			throw new DuplicateResourceException("Você já tem uma conta com esse nome");
		}
	}

	@Transactional
	public void delete(UUID userId, UUID accountId) {
		accountRepository.delete(findOwned(userId, accountId));
	}

	private Account findOwned(UUID userId, UUID accountId) {
		return accountRepository.findByIdAndUserId(accountId, userId)
				.orElseThrow(() -> new NotFoundException("Conta não encontrada"));
	}

}
