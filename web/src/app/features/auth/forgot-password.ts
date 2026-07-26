import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-forgot-password',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './forgot-password.html',
  styleUrl: './login.css'
})
export class ForgotPassword {
  private readonly formBuilder = inject(FormBuilder);
  private readonly authService = inject(AuthService);

  readonly loading = signal(false);
  readonly sent = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email]]
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.errorMessage.set(null);
    this.authService.forgotPassword(this.form.getRawValue().email).subscribe({
      // Sempre a mesma mensagem neutra (anti-enumeração): não revela se o email existe.
      next: () => {
        this.loading.set(false);
        this.sent.set(true);
      },
      error: (error: HttpErrorResponse) => {
        this.loading.set(false);
        if (error.status === 429) {
          this.errorMessage.set('Muitas tentativas. Aguarde alguns minutos e tente novamente.');
        } else {
          this.errorMessage.set('Erro ao comunicar com o servidor. Tente novamente.');
        }
      }
    });
  }
}
