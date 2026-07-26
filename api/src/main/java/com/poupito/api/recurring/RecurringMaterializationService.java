package com.poupito.api.recurring;

import com.poupito.api.account.Account;
import com.poupito.api.account.AccountRepository;
import com.poupito.api.card.Card;
import com.poupito.api.card.CardRepository;
import com.poupito.api.category.Category;
import com.poupito.api.category.CategoryRepository;
import com.poupito.api.invoice.CardInvoice;
import com.poupito.api.invoice.CardInvoiceService;
import com.poupito.api.recurring.dto.OccurrenceResponse;
import com.poupito.api.transaction.PaymentMethod;
import com.poupito.api.transaction.Transaction;
import com.poupito.api.transaction.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Service
public class RecurringMaterializationService {

	private static final Logger log = LoggerFactory.getLogger(RecurringMaterializationService.class);

	private final RecurringTransactionRepository recurringRepository;
	private final TransactionRepository transactionRepository;
	private final AccountRepository accountRepository;
	private final CardRepository cardRepository;
	private final CategoryRepository categoryRepository;
	private final CardInvoiceService cardInvoiceService;

	public RecurringMaterializationService(RecurringTransactionRepository recurringRepository,
			TransactionRepository transactionRepository, AccountRepository accountRepository,
			CardRepository cardRepository, CategoryRepository categoryRepository,
			CardInvoiceService cardInvoiceService) {
		this.recurringRepository = recurringRepository;
		this.transactionRepository = transactionRepository;
		this.accountRepository = accountRepository;
		this.cardRepository = cardRepository;
		this.categoryRepository = categoryRepository;
		this.cardInvoiceService = cardInvoiceService;
	}

	/** Materializa os fixos ativos do usuário no mês e devolve o estado das ocorrências. */
	@Transactional
	public List<OccurrenceResponse> materializeForUserAndMonth(UUID userId, YearMonth month) {
		recurringRepository.findAllByUserIdOrderByDescriptionAsc(userId).stream()
				.filter(recurring -> recurring.occursIn(month))
				.forEach(recurring -> materializeOccurrence(recurring, month));
		return occurrencesFor(userId, month);
	}

	@Transactional(readOnly = true)
	public List<OccurrenceResponse> occurrencesFor(UUID userId, YearMonth month) {
		return recurringRepository.findAllByUserIdOrderByDescriptionAsc(userId).stream()
				.filter(recurring -> recurring.occursIn(month))
				.map(recurring -> toOccurrence(userId, recurring, month))
				.toList();
	}

	/** Cria a transação da ocorrência do mês se ainda não existir (idempotente). */
	@Transactional
	public Transaction materializeOccurrence(RecurringTransaction recurring, YearMonth month) {
		return transactionRepository
				.findByRecurringIdAndDateBetween(recurring.getId(), month.atDay(1), month.atEndOfMonth())
				.orElseGet(() -> createOccurrence(recurring, month));
	}

	/** Job mensal: materializa todos os fixos ativos no mês corrente. */
	@Scheduled(cron = "${app.recurring.materialize-cron:0 0 3 1 * *}")
	@Transactional
	public void materializeCurrentMonth() {
		YearMonth month = YearMonth.now();
		List<RecurringTransaction> active = recurringRepository.findAllByActiveTrue();
		log.info("Materializando {} fixos ativos para {}", active.size(), month);
		active.stream()
				.filter(recurring -> recurring.occursIn(month))
				.forEach(recurring -> materializeOccurrence(recurring, month));
	}

	private Transaction createOccurrence(RecurringTransaction recurring, YearMonth month) {
		LocalDate date = recurring.occurrenceDate(month);
		// fixo no cartão (sessão #32) entra na fatura do período pelo closing_day, como qualquer
		// compra no crédito; fixo em conta debita direto e nasce não-pago (checkbox "pago?").
		Transaction transaction = recurring.isOnCard()
				? createCardOccurrence(recurring, date)
				: createAccountOccurrence(recurring, date);
		return transactionRepository.save(transaction);
	}

	private Transaction createAccountOccurrence(RecurringTransaction recurring, LocalDate date) {
		accountRepository.findById(recurring.getAccountId())
				.orElseThrow(() -> new IllegalStateException("Conta do fixo não encontrada"));
		return Transaction.materialized(recurring.getUserId(), recurring.getAccountId(),
				recurring.getCategoryId(), recurring.getDescription(), recurring.getAmount(),
				date, recurring.getType(), recurring.getId());
	}

	private Transaction createCardOccurrence(RecurringTransaction recurring, LocalDate date) {
		Card card = cardRepository.findById(recurring.getCardId())
				.orElseThrow(() -> new IllegalStateException("Cartão do fixo não encontrado"));
		CardInvoice invoice = cardInvoiceService.getOrCreateInvoiceFor(card, date);
		return Transaction.materializedOnCard(recurring.getUserId(), card.getId(),
				recurring.getCategoryId(), invoice.getId(), recurring.getDescription(),
				recurring.getAmount(), date, recurring.getType(), recurring.getId());
	}

	private OccurrenceResponse toOccurrence(UUID userId, RecurringTransaction recurring, YearMonth month) {
		Category category = categoryRepository.findByIdAndUserId(recurring.getCategoryId(), userId).orElse(null);
		Account account = recurring.getAccountId() == null ? null
				: accountRepository.findByIdAndUserId(recurring.getAccountId(), userId).orElse(null);
		Card card = recurring.getCardId() == null ? null
				: cardRepository.findByIdAndUserId(recurring.getCardId(), userId).orElse(null);
		Transaction existing = transactionRepository
				.findByRecurringIdAndDateBetween(recurring.getId(), month.atDay(1), month.atEndOfMonth())
				.orElse(null);
		return new OccurrenceResponse(
				recurring.getId(),
				recurring.getDescription(),
				recurring.getAmount(),
				recurring.getType(),
				account != null ? account.getName() : null,
				card != null ? card.getName() : null,
				PaymentMethod.of(recurring.getCardId(), account != null ? account.getType() : null),
				category != null ? category.getName() : null,
				category != null ? category.getIcon() : null,
				category != null ? category.getColor() : null,
				recurring.getDayOfMonth(),
				recurring.occurrenceDate(month),
				existing != null ? existing.getId() : null,
				existing != null,
				existing != null && existing.isPaid());
	}

}
