import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { PASSWORD_POLICY_HINT, strongPassword } from '../../core/auth/password.validator';

type Mode = 'login' | 'register';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './login.html',
  styleUrl: './login.css'
})
export class Login {
  private readonly formBuilder = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly mode = signal<Mode>('login');
  readonly errorMessage = signal<string | null>(null);
  readonly loading = signal(false);
  readonly showPassword = signal(false);

  readonly passwordHint = PASSWORD_POLICY_HINT;

  readonly form = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]]
  });

  constructor() {
    if (this.route.snapshot.queryParamMap.get('mode') === 'register') {
      this.setMode('register');
    }
  }

  toggleMode(): void {
    this.setMode(this.mode() === 'login' ? 'register' : 'login');
  }

  private setMode(next: Mode): void {
    this.mode.set(next);
    this.errorMessage.set(null);
    // A política forte só vale onde se DEFINE senha (cadastro). No login, apenas "obrigatória".
    const password = this.form.controls.password;
    password.setValidators(next === 'register' ? [Validators.required, strongPassword] : [Validators.required]);
    password.updateValueAndValidity();
  }

  toggleShowPassword(): void {
    this.showPassword.update((v) => !v);
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { email, password } = this.form.getRawValue();
    const request$ =
      this.mode() === 'login'
        ? this.authService.login(email, password)
        : this.authService.register(email, password);

    this.loading.set(true);
    this.errorMessage.set(null);
    request$.subscribe({
      next: () => this.router.navigate(['/dashboard']),
      error: (error: HttpErrorResponse) => {
        this.loading.set(false);
        this.errorMessage.set(this.messageFor(error));
      }
    });
  }

  private messageFor(error: HttpErrorResponse): string {
    if (error.status === 401) {
      return 'Email ou senha inválidos';
    }
    if (error.status === 409) {
      return 'Email já cadastrado';
    }
    if (error.status === 429) {
      const retryAfter = Number(error.headers?.get('Retry-After'));
      if (retryAfter > 0) {
        const minutes = Math.ceil(retryAfter / 60);
        return `Muitas tentativas. Tente novamente em ${minutes} min.`;
      }
      return 'Muitas tentativas. Aguarde alguns minutos e tente novamente.';
    }
    return 'Erro ao comunicar com o servidor. Tente novamente.';
  }
}
