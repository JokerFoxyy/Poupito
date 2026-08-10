import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { MonthPicker } from '../../shared/month-picker';
import { AccountStore } from '../../core/state/account.store';
import { CardStore } from '../../core/state/card.store';
import { CategoryStore } from '../../core/state/category.store';
import { Card, Category, PAYMENT_METHOD_LABELS } from '../settings/settings.models';
import { RecurringService } from './recurring.service';
import { Occurrence, Recurring as RecurringModel, RecurringType } from './recurring.models';

@Component({
  selector: 'app-recurring',
  imports: [ReactiveFormsModule, MonthPicker, CurrencyPipe],
  templateUrl: './recurring.html',
  styleUrl: './recurring.css'
})
export class Recurring implements OnInit {
  private readonly recurringService = inject(RecurringService);
  private readonly accountStore = inject(AccountStore);
  private readonly cardStore = inject(CardStore);
  private readonly categoryStore = inject(CategoryStore);
  private readonly formBuilder = inject(FormBuilder);

  readonly month = signal(currentMonth());
  readonly recurrings = signal<RecurringModel[]>([]);
  readonly occurrences = signal<Occurrence[]>([]);
  readonly accounts = this.accountStore.accounts;
  readonly cards = this.cardStore.cards;
  readonly categories = this.categoryStore.categories;
  readonly showForm = signal(false);
  readonly editing = signal<RecurringModel | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly methodLabels = PAYMENT_METHOD_LABELS;
  /** Buscado à parte da listagem principal — só esta tela precisa ver arquivados (sessão #42). */
  readonly archivedRecurrings = signal<RecurringModel[]>([]);

  /**
   * Totais do mês selecionado. Somam as **ocorrências** (não a lista crua de fixos), então já
   * respeitam quem realmente incide no mês: inativos e encerrados ficam de fora.
   */
  readonly totalExpenses = computed(() => this.sumByType('EXPENSE'));
  readonly totalIncome = computed(() => this.sumByType('INCOME'));

  readonly form = this.formBuilder.nonNullable.group({
    description: ['', [Validators.required, Validators.maxLength(200)]],
    amount: [null as number | null, [Validators.required, Validators.min(0.01)]],
    type: ['EXPENSE' as RecurringType, Validators.required],
    // "Pagar com": account:<id> ou card:<id> (mesmo padrão de Transações, sessão #25)
    target: ['', Validators.required],
    categoryId: ['', Validators.required],
    dayOfMonth: [1, [Validators.required, Validators.min(1), Validators.max(31)]],
    active: [true],
    endDate: ['']
  });

  ngOnInit(): void {
    this.accountStore.ensureLoaded();
    this.cardStore.ensureLoaded();
    this.categoryStore.ensureLoaded();
    this.load();
    this.loadArchived();
  }

  onMonthChange(month: string): void {
    this.month.set(month);
    this.loadOccurrences();
  }

  materialize(): void {
    this.recurringService.materialize(this.month()).subscribe({
      next: (occurrences) => this.occurrences.set(occurrences),
      error: () => this.errorMessage.set('Erro ao gerar os lançamentos do mês')
    });
  }

  occurrenceFor(recurringId: string): Occurrence | undefined {
    return this.occurrences().find((occurrence) => occurrence.recurringId === recurringId);
  }

  togglePaid(occurrence: Occurrence): void {
    if (!occurrence.transactionId) {
      return;
    }
    this.recurringService.setPaid(occurrence.transactionId, !occurrence.paid).subscribe({
      next: () => this.loadOccurrences(),
      error: () => this.errorMessage.set('Erro ao atualizar o pagamento')
    });
  }

  categoriesForType(): Category[] {
    const kind = this.form.controls.type.value === 'INCOME' ? 'INCOME' : 'EXPENSE';
    return this.categories().filter((category) => category.kind === kind);
  }

  /** Cartão só existe no crédito, que é sempre gasto: em entradas, esconde os cartões. */
  cardsForType(): Card[] {
    return this.form.controls.type.value === 'INCOME' ? [] : this.cards();
  }

  /** Fixo no cartão: a ocorrência entra na fatura, então não há checkbox "pago?" por mês. */
  isCardSelected(): boolean {
    return this.form.controls.target.value.startsWith('card:');
  }

  onTypeChange(): void {
    const options = this.categoriesForType();
    if (!options.some((category) => category.id === this.form.controls.categoryId.value)) {
      this.form.controls.categoryId.setValue(options[0]?.id ?? '');
    }
    // entrada não pode cair em cartão: se havia um cartão escolhido, volta pra primeira conta
    if (this.form.controls.type.value === 'INCOME' && this.isCardSelected()) {
      this.form.controls.target.setValue(this.accounts()[0] ? `account:${this.accounts()[0].id}` : '');
    }
  }


  openCreate(): void {
    this.editing.set(null);
    this.errorMessage.set(null);
    this.form.reset({
      description: '',
      amount: null,
      type: 'EXPENSE',
      target: this.accounts()[0] ? `account:${this.accounts()[0].id}` : '',
      categoryId: '',
      dayOfMonth: 1,
      active: true,
      endDate: ''
    });
    this.onTypeChange();
    this.showForm.set(true);
  }

  openEdit(recurring: RecurringModel): void {
    this.editing.set(recurring);
    this.errorMessage.set(null);
    this.form.reset({
      description: recurring.description,
      amount: recurring.amount,
      type: recurring.type,
      target: recurring.cardId ? `card:${recurring.cardId}` : `account:${recurring.accountId}`,
      categoryId: recurring.categoryId,
      dayOfMonth: recurring.dayOfMonth,
      active: recurring.active,
      endDate: recurring.endDate ?? ''
    });
    this.showForm.set(true);
  }

  cancel(): void {
    this.showForm.set(false);
    this.editing.set(null);
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const onCard = raw.target.startsWith('card:');
    const targetId = raw.target.slice(raw.target.indexOf(':') + 1);
    const payload = {
      description: raw.description,
      amount: raw.amount as number,
      type: raw.type,
      // conta XOR cartão: manda exatamente um dos dois (o backend rejeita os dois juntos)
      ...(onCard ? { cardId: targetId } : { accountId: targetId }),
      categoryId: raw.categoryId,
      dayOfMonth: raw.dayOfMonth,
      active: raw.active,
      endDate: raw.endDate || null
    };
    const editing = this.editing();
    const request$ = editing
      ? this.recurringService.update(editing.id, payload)
      : this.recurringService.create(payload);
    request$.subscribe({
      next: () => {
        this.cancel();
        this.load();
      },
      error: (error) =>
        this.errorMessage.set(error?.error?.message ?? 'Erro ao salvar o fixo')
    });
  }

  remove(recurring: RecurringModel): void {
    this.recurringService.delete(recurring.id).subscribe({
      next: () => this.load(),
      error: () => this.errorMessage.set(
        'Não foi possível excluir: pode ter ocorrência/transação vinculada. ' +
        'Use "Arquivar" para tirar da tela sem perder o histórico.')
    });
  }

  archive(recurring: RecurringModel): void {
    this.recurringService.archive(recurring.id).subscribe({
      next: () => {
        this.load();
        this.loadArchived();
      },
      error: () => this.errorMessage.set('Erro ao arquivar o fixo')
    });
  }

  unarchive(recurring: RecurringModel): void {
    this.recurringService.unarchive(recurring.id).subscribe({
      next: () => {
        this.load();
        this.loadArchived();
      },
      error: () => this.errorMessage.set('Erro ao desarquivar o fixo')
    });
  }

  private load(): void {
    this.recurringService.list().subscribe((recurrings) => this.recurrings.set(recurrings));
    this.loadOccurrences();
  }

  private loadArchived(): void {
    this.recurringService.list(true).subscribe((recurrings) => this.archivedRecurrings.set(recurrings));
  }

  private loadOccurrences(): void {
    this.recurringService.occurrences(this.month()).subscribe((occurrences) => this.occurrences.set(occurrences));
  }

  private sumByType(type: RecurringType): number {
    return this.occurrences()
      .filter((occurrence) => occurrence.type === type)
      .reduce((total, occurrence) => total + occurrence.amount, 0);
  }
}

function currentMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}
