import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { AccountStore } from '../../core/state/account.store';
import { ACCOUNT_TYPE_LABELS, Account, AccountType } from './settings.models';

@Component({
  selector: 'app-accounts-panel',
  imports: [ReactiveFormsModule],
  templateUrl: './accounts-panel.html'
})
export class AccountsPanel implements OnInit {
  private readonly accountStore = inject(AccountStore);
  private readonly formBuilder = inject(FormBuilder);

  readonly accounts = this.accountStore.accounts;
  readonly editing = signal<Account | null>(null);
  readonly showForm = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly typeLabels = ACCOUNT_TYPE_LABELS;
  readonly typeOptions = Object.keys(ACCOUNT_TYPE_LABELS) as AccountType[];

  readonly form = this.formBuilder.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    type: ['CHECKING' as AccountType, Validators.required]
  });

  ngOnInit(): void {
    this.accountStore.ensureLoaded();
  }

  openCreate(): void {
    this.editing.set(null);
    this.form.reset({ name: '', type: 'CHECKING' });
    this.showForm.set(true);
    this.errorMessage.set(null);
  }

  openEdit(account: Account): void {
    this.editing.set(account);
    this.form.reset({ name: account.name, type: account.type });
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
    const payload = this.form.getRawValue();
    const editing = this.editing();
    const request$ = editing
      ? this.accountStore.update(editing.id, payload)
      : this.accountStore.create(payload);
    request$.subscribe({
      next: () => this.cancel(),
      error: (error: unknown) => this.errorMessage.set(this.saveErrorMessage(error))
    });
  }

  /** 409 = nome de conta repetido (unicidade por usuário, sessão #34): mostra o motivo real. */
  private saveErrorMessage(error: unknown): string {
    if (error && typeof error === 'object' && 'status' in error
        && (error as { status: number }).status === 409) {
      const message = (error as { error?: { message?: string } }).error?.message;
      return message ?? 'Você já tem uma conta com esse nome';
    }
    return 'Erro ao salvar a conta';
  }

  remove(account: Account): void {
    this.accountStore.delete(account.id).subscribe({
      error: () => this.errorMessage.set(
        'Não foi possível excluir: a conta pode ter cartões, transações ou faturas vinculados.')
    });
  }
}
