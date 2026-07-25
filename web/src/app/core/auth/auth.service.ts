import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, finalize, tap } from 'rxjs';

import { UserResponse } from './auth.models';

// Flag NÃO-sensível apenas para roteamento no cliente. A credencial real é o cookie
// httpOnly (inacessível ao JS); esta flag só evita um flicker de tela ao recarregar.
const AUTH_FLAG = 'poupito.authed';
const API = '/api/v1/auth';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  private readonly authed = signal<boolean>(localStorage.getItem(AUTH_FLAG) === '1');
  readonly isAuthenticated = this.authed.asReadonly();
  readonly currentUser = signal<UserResponse | null>(null);

  register(email: string, password: string): Observable<UserResponse> {
    return this.http
      .post<UserResponse>(`${API}/register`, { email, password })
      .pipe(tap((user) => this.markAuthenticated(user)));
  }

  login(email: string, password: string): Observable<UserResponse> {
    return this.http
      .post<UserResponse>(`${API}/login`, { email, password })
      .pipe(tap((user) => this.markAuthenticated(user)));
  }

  refresh(): Observable<UserResponse> {
    return this.http
      .post<UserResponse>(`${API}/refresh`, {})
      .pipe(tap((user) => this.markAuthenticated(user)));
  }

  /** Solicita o link de redefinição. Resposta 204 sempre (anti-enumeração — não revela o email). */
  forgotPassword(email: string): Observable<void> {
    return this.http.post<void>(`${API}/forgot-password`, { email });
  }

  /** Redefine a senha com o token recebido por email. */
  resetPassword(token: string, newPassword: string): Observable<void> {
    return this.http.post<void>(`${API}/reset-password`, { token, newPassword });
  }

  loadCurrentUser(): Observable<UserResponse> {
    return this.http
      .get<UserResponse>(`${API}/me`)
      .pipe(tap((user) => this.markAuthenticated(user)));
  }

  /** Encerra a sessão no servidor (revoga o refresh token) e limpa o estado local. */
  logout(): Observable<void> {
    return this.http.post<void>(`${API}/logout`, {}).pipe(finalize(() => this.clearSession()));
  }

  /** Limpeza local sem chamada de rede (usada quando o refresh falha). */
  clearSession(): void {
    this.currentUser.set(null);
    this.authed.set(false);
    localStorage.removeItem(AUTH_FLAG);
  }

  private markAuthenticated(user: UserResponse): void {
    this.currentUser.set(user);
    this.authed.set(true);
    localStorage.setItem(AUTH_FLAG, '1');
  }
}
