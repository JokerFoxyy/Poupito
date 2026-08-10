import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Card } from './settings.models';

const API = '/api/v1/cards';

export interface CardPayload {
  name: string;
  accountId: string;
  closingDay: number;
  dueDay: number;
}

@Injectable({ providedIn: 'root' })
export class CardService {
  private readonly http = inject(HttpClient);

  list(archived = false): Observable<Card[]> {
    return this.http.get<Card[]>(API, { params: { archived } });
  }

  create(payload: CardPayload): Observable<Card> {
    return this.http.post<Card>(API, payload);
  }

  update(id: string, payload: CardPayload): Observable<Card> {
    return this.http.put<Card>(`${API}/${id}`, payload);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${API}/${id}`);
  }

  /** Some da tela principal e dos seletores "Pagar com", mas o histórico é preservado (sessão #42). */
  archive(id: string): Observable<Card> {
    return this.http.patch<Card>(`${API}/${id}/archive`, {});
  }

  unarchive(id: string): Observable<Card> {
    return this.http.patch<Card>(`${API}/${id}/unarchive`, {});
  }
}
