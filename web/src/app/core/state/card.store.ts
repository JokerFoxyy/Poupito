import { Injectable, inject, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';

import { CardPayload, CardService } from '../../features/settings/card.service';
import { Card } from '../../features/settings/settings.models';

/**
 * Fonte única da lista de cartões — mesma motivação do {@link AccountStore}
 * (sessão #26): apagar um cartão em Configurações precisa sumir na hora dos
 * dropdowns de Transações/Import sem reload.
 */
@Injectable({ providedIn: 'root' })
export class CardStore {
  private readonly service = inject(CardService);
  private readonly _cards = signal<Card[]>([]);
  private loaded = false;

  readonly cards = this._cards.asReadonly();

  ensureLoaded(): void {
    if (!this.loaded) {
      this.loaded = true;
      this.refresh();
    }
  }

  refresh(): void {
    this.service.list().subscribe((cards) => this._cards.set(cards));
  }

  /** Mesma correção do AccountStore.reset() — bug de vazamento entre usuários (sessão #42). */
  reset(): void {
    this._cards.set([]);
    this.loaded = false;
  }

  create(payload: CardPayload): Observable<Card> {
    return this.service.create(payload).pipe(tap(() => this.refresh()));
  }

  update(id: string, payload: CardPayload): Observable<Card> {
    return this.service.update(id, payload).pipe(tap(() => this.refresh()));
  }

  delete(id: string): Observable<void> {
    return this.service.delete(id).pipe(tap(() => this.refresh()));
  }
}
