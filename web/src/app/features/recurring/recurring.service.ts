import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Occurrence, Recurring, RecurringPayload } from './recurring.models';

const API = '/api/v1/recurring';

@Injectable({ providedIn: 'root' })
export class RecurringService {
  private readonly http = inject(HttpClient);

  list(archived = false): Observable<Recurring[]> {
    return this.http.get<Recurring[]>(API, { params: { archived } });
  }

  create(payload: RecurringPayload): Observable<Recurring> {
    return this.http.post<Recurring>(API, payload);
  }

  update(id: string, payload: RecurringPayload): Observable<Recurring> {
    return this.http.put<Recurring>(`${API}/${id}`, payload);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${API}/${id}`);
  }

  occurrences(month: string): Observable<Occurrence[]> {
    return this.http.get<Occurrence[]>(`${API}/occurrences`, { params: new HttpParams().set('month', month) });
  }

  /** Materializa (idempotente) os fixos do mês e devolve as ocorrências. */
  materialize(month: string): Observable<Occurrence[]> {
    return this.http.post<Occurrence[]>(`${API}/materialize`, {}, { params: new HttpParams().set('month', month) });
  }

  setPaid(transactionId: string, paid: boolean): Observable<unknown> {
    return this.http.put(`/api/v1/transactions/${transactionId}/paid`, { paid });
  }

  /** Some da tela principal e dos seletores "Pagar com", mas o histórico é preservado (sessão #42). */
  archive(id: string): Observable<Recurring> {
    return this.http.patch<Recurring>(`${API}/${id}/archive`, {});
  }

  unarchive(id: string): Observable<Recurring> {
    return this.http.patch<Recurring>(`${API}/${id}/unarchive`, {});
  }
}
