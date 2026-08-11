import { Injectable, inject, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';

import { AccountPayload, AccountService } from '../../features/settings/account.service';
import { Account } from '../../features/settings/settings.models';

/**
 * Fonte única da lista de contas no app. Antes cada tela carregava a lista
 * no próprio `ngOnInit` e nunca ressincronizava — apagar uma conta em
 * Configurações deixava os dropdowns das outras telas mostrando contas
 * fantasma, e salvar algo vinculado a elas dava 404 (sessão #26).
 *
 * Agora toda mutação passa pelo store e dá `refresh()`, propagando pro signal
 * que todos os consumidores leem.
 */
@Injectable({ providedIn: 'root' })
export class AccountStore {
  private readonly service = inject(AccountService);
  private readonly _accounts = signal<Account[]>([]);
  private loaded = false;

  readonly accounts = this._accounts.asReadonly();

  /** Carrega uma vez; idempotente — consumidores chamam no ngOnInit sem coordenar. */
  ensureLoaded(): void {
    if (!this.loaded) {
      this.loaded = true;
      this.refresh();
    }
  }

  refresh(): void {
    this.service.list().subscribe((accounts) => this._accounts.set(accounts));
  }

  /**
   * Chamado por `AuthService.clearSession()` no logout — sem isso, o próximo usuário a logar na
   * mesma aba (sem reload de página) via `ensureLoaded()` encontraria `loaded=true` e nunca
   * recarregaria, herdando os dados em memória do usuário anterior (bug de vazamento, sessão #42).
   */
  reset(): void {
    this._accounts.set([]);
    this.loaded = false;
  }

  create(payload: AccountPayload): Observable<Account> {
    return this.service.create(payload).pipe(tap(() => this.refresh()));
  }

  update(id: string, payload: AccountPayload): Observable<Account> {
    return this.service.update(id, payload).pipe(tap(() => this.refresh()));
  }

  delete(id: string): Observable<void> {
    return this.service.delete(id).pipe(tap(() => this.refresh()));
  }
}
