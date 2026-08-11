import { Injectable, inject, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';

import { CategoryPayload, CategoryService } from '../../features/settings/category.service';
import { Category } from '../../features/settings/settings.models';

/**
 * Fonte única da lista de categorias — mesma motivação do {@link AccountStore}
 * (sessão #26): mutações em Configurações propagam pros selects de
 * Transações/Import/Orçamentos sem reload.
 */
@Injectable({ providedIn: 'root' })
export class CategoryStore {
  private readonly service = inject(CategoryService);
  private readonly _categories = signal<Category[]>([]);
  private loaded = false;

  readonly categories = this._categories.asReadonly();

  ensureLoaded(): void {
    if (!this.loaded) {
      this.loaded = true;
      this.refresh();
    }
  }

  refresh(): void {
    this.service.list().subscribe((categories) => this._categories.set(categories));
  }

  /** Mesma correção do AccountStore.reset() — bug de vazamento entre usuários (sessão #42). */
  reset(): void {
    this._categories.set([]);
    this.loaded = false;
  }

  create(payload: CategoryPayload): Observable<Category> {
    return this.service.create(payload).pipe(tap(() => this.refresh()));
  }

  update(id: string, payload: CategoryPayload): Observable<Category> {
    return this.service.update(id, payload).pipe(tap(() => this.refresh()));
  }

  delete(id: string): Observable<void> {
    return this.service.delete(id).pipe(tap(() => this.refresh()));
  }
}
