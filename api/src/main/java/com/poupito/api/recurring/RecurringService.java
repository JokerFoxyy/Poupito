package com.poupito.api.recurring;

import com.poupito.api.account.Account;
import com.poupito.api.account.AccountRepository;
import com.poupito.api.card.Card;
import com.poupito.api.card.CardRepository;
import com.poupito.api.category.Category;
import com.poupito.api.category.CategoryKind;
import com.poupito.api.category.CategoryRepository;
import com.poupito.api.common.error.BusinessException;
import com.poupito.api.common.error.NotFoundException;
import com.poupito.api.recurring.dto.RecurringRequest;
import com.poupito.api.recurring.dto.RecurringResponse;
import com.poupito.api.transaction.TransactionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class RecurringService {

	private final RecurringTransactionRepository recurringRepository;
	private final AccountRepository accountRepository;
	private final CardRepository cardRepository;
	private final CategoryRepository categoryRepository;

	public RecurringService(RecurringTransactionRepository recurringRepository,
			AccountRepository accountRepository, CardRepository cardRepository,
			CategoryRepository categoryRepository) {
		this.recurringRepository = recurringRepository;
		this.accountRepository = accountRepository;
		this.cardRepository = cardRepository;
		this.categoryRepository = categoryRepository;
	}

	@Transactional(readOnly = true)
	public List<RecurringResponse> list(UUID userId, boolean archived) {
		return recurringRepository.findAllByUserIdAndArchivedOrderByDescriptionAsc(userId, archived).stream()
				.map(recurring -> toResponse(userId, recurring))
				.toList();
	}

	@Transactional
	public RecurringResponse create(UUID userId, RecurringRequest request) {
		ValidatedRefs refs = validate(userId, request);
		RecurringTransaction recurring = new RecurringTransaction(userId, refs.accountId(), refs.cardId(),
				refs.category().getId(), request.description().trim(), request.amount(), request.type(),
				request.dayOfMonth(), request.activeOrDefault(), request.endDate());
		recurringRepository.save(recurring);
		return RecurringResponse.from(recurring, refs.account(), refs.card(), refs.category());
	}

	@Transactional
	public RecurringResponse update(UUID userId, UUID recurringId, RecurringRequest request) {
		RecurringTransaction recurring = findOwned(userId, recurringId);
		ValidatedRefs refs = validate(userId, request);
		recurring.update(refs.accountId(), refs.cardId(), refs.category().getId(),
				request.description().trim(), request.amount(), request.type(), request.dayOfMonth(),
				request.activeOrDefault(), request.endDate());
		return RecurringResponse.from(recurring, refs.account(), refs.card(), refs.category());
	}

	@Transactional
	public void delete(UUID userId, UUID recurringId) {
		recurringRepository.delete(findOwned(userId, recurringId));
	}

	/**
	 * Some da tela principal e dos seletores "Pagar com" e para de materializar ocorrência nova,
	 * mas o histórico (ocorrências/transações já lançadas) continua intacto — alternativa ao
	 * delete quando há vínculo (sessão #42).
	 */
	@Transactional
	public RecurringResponse archive(UUID userId, UUID recurringId) {
		RecurringTransaction recurring = findOwned(userId, recurringId);
		recurring.archive();
		return toResponse(userId, recurring);
	}

	@Transactional
	public RecurringResponse unarchive(UUID userId, UUID recurringId) {
		RecurringTransaction recurring = findOwned(userId, recurringId);
		recurring.unarchive();
		return toResponse(userId, recurring);
	}

	private RecurringTransaction findOwned(UUID userId, UUID recurringId) {
		return recurringRepository.findByIdAndUserId(recurringId, userId)
				.orElseThrow(() -> new NotFoundException("Fixo não encontrado"));
	}

	private RecurringResponse toResponse(UUID userId, RecurringTransaction recurring) {
		Account account = recurring.getAccountId() == null ? null
				: accountRepository.findByIdAndUserId(recurring.getAccountId(), userId).orElse(null);
		Card card = recurring.getCardId() == null ? null
				: cardRepository.findByIdAndUserId(recurring.getCardId(), userId).orElse(null);
		Category category = categoryRepository.findByIdAndUserId(recurring.getCategoryId(), userId).orElse(null);
		return RecurringResponse.from(recurring, account, card, category);
	}

	private ValidatedRefs validate(UUID userId, RecurringRequest request) {
		if (request.type() != TransactionType.EXPENSE && request.type() != TransactionType.INCOME) {
			throw new BusinessException("Fixo deve ser gasto ou entrada");
		}
		if (!request.hasExactlyOneTarget()) {
			throw new BusinessException("Informe conta OU cartão");
		}
		if (request.onCard() && request.type() == TransactionType.INCOME) {
			throw new BusinessException("Entrada não pode ser lançada em cartão de crédito");
		}
		Category category = categoryRepository.findByIdAndUserId(request.categoryId(), userId)
				.orElseThrow(() -> new NotFoundException("Categoria não encontrada"));
		CategoryKind expectedKind = request.type() == TransactionType.EXPENSE
				? CategoryKind.EXPENSE
				: CategoryKind.INCOME;
		if (category.getKind() != expectedKind) {
			throw new BusinessException("Categoria não é compatível com o tipo do fixo");
		}
		if (request.onCard()) {
			Card card = cardRepository.findByIdAndUserId(request.cardId(), userId)
					.orElseThrow(() -> new NotFoundException("Cartão não encontrado"));
			return new ValidatedRefs(null, card, category);
		}
		Account account = accountRepository.findByIdAndUserId(request.accountId(), userId)
				.orElseThrow(() -> new NotFoundException("Conta não encontrada"));
		return new ValidatedRefs(account, null, category);
	}

	private record ValidatedRefs(Account account, Card card, Category category) {

		UUID accountId() {
			return account == null ? null : account.getId();
		}

		UUID cardId() {
			return card == null ? null : card.getId();
		}

	}

}
