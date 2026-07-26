import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { AccountStore } from '../../core/state/account.store';
import { CardStore } from '../../core/state/card.store';
import { Card } from './settings.models';

@Component({
  selector: 'app-cards-panel',
  imports: [ReactiveFormsModule],
  templateUrl: './cards-panel.html'
})
export class CardsPanel implements OnInit {
  private readonly cardStore = inject(CardStore);
  private readonly accountStore = inject(AccountStore);
  private readonly formBuilder = inject(FormBuilder);

  readonly cards = this.cardStore.cards;
  readonly accounts = this.accountStore.accounts;
  readonly editing = signal<Card | null>(null);
  readonly showForm = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.formBuilder.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    accountId: ['', Validators.required],
    closingDay: [null as number | null, [Validators.required, Validators.min(1), Validators.max(31)]],
    dueDay: [null as number | null, [Validators.required, Validators.min(1), Validators.max(31)]]
  });

  ngOnInit(): void {
    this.cardStore.ensureLoaded();
    this.accountStore.ensureLoaded();
  }

  openCreate(): void {
    this.editing.set(null);
    this.form.reset({ name: '', accountId: '', closingDay: null, dueDay: null });
    this.showForm.set(true);
    this.errorMessage.set(null);
  }

  openEdit(card: Card): void {
    this.editing.set(card);
    this.form.reset({
      name: card.name,
      accountId: card.accountId,
      closingDay: card.closingDay,
      dueDay: card.dueDay
    });
    this.showForm.set(true);
    this.errorMessage.set(null);
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
    const payload = {
      name: raw.name,
      accountId: raw.accountId,
      closingDay: raw.closingDay!,
      dueDay: raw.dueDay!
    };
    const editing = this.editing();
    const request$ = editing
      ? this.cardStore.update(editing.id, payload)
      : this.cardStore.create(payload);
    request$.subscribe({
      next: () => this.cancel(),
      error: (error) => this.errorMessage.set(this.saveErrorMessage(error))
    });
  }

  remove(card: Card): void {
    this.cardStore.delete(card.id).subscribe({
      error: () => this.errorMessage.set(
        'Não foi possível excluir: o cartão pode ter transações ou faturas vinculadas.')
    });
  }

  /**
   * 404 na conta vinculada = a conta foi apagada noutra tela e o dropdown ficou velho.
   * 409 = nome de cartão repetido (unicidade por usuário, sessão #34).
   */
  private saveErrorMessage(error: unknown): string {
    if (!error || typeof error !== 'object' || !('status' in error)) {
      return 'Erro ao salvar o cartão';
    }
    const status = (error as { status: number }).status;
    if (status === 404) {
      this.accountStore.refresh();
      return 'A conta selecionada não existe mais — atualize a lista e escolha outra.';
    }
    if (status === 409) {
      const message = (error as { error?: { message?: string } }).error?.message;
      return message ?? 'Você já tem um cartão com esse nome';
    }
    return 'Erro ao salvar o cartão';
  }
}
