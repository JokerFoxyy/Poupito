import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { PASSWORD_POLICY_HINT, strongPassword } from '../../core/auth/password.validator';

@Component({
  selector: 'app-reset-password',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './reset-password.html',
  styleUrl: './login.css'
})
export class ResetPassword {
  private readonly formBuilder = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);

  private readonly token = this.route.snapshot.queryParamMap.get('token') ?? '';

  readonly hasToken = this.token.length > 0;
  readonly loading = signal(false);
  readonly done = signal(false);
  readonly showPassword = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly passwordHint = PASSWORD_POLICY_HINT;

  readonly form = this.formBuilder.nonNullable.group({
    newPassword: ['', [Validators.required, strongPassword]]
  });

  toggleShowPassword(): void {
    this.showPassword.update((v) => !v);
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.errorMessage.set(null);
    this.authService.resetPassword(this.token, this.form.getRawValue().newPassword).subscribe({
      next: () => {
        this.loading.set(false);
        this.done.set(true);
      },
      error: (error: HttpErrorResponse) => {
        this.loading.set(false);
        if (error.status === 400) {
          this.errorMessage.set('Link inválido ou expirado. Solicite um novo.');
        } else {
          this.errorMessage.set('Erro ao comunicar com o servidor. Tente novamente.');
        }
      }
    });
  }
}
