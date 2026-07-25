import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { ForgotPassword } from './forgot-password';
import { AuthService } from '../../core/auth/auth.service';

describe('ForgotPassword', () => {
  let fixture: ComponentFixture<ForgotPassword>;
  let component: ForgotPassword;
  let authService: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    authService = jasmine.createSpyObj<AuthService>('AuthService', ['forgotPassword']);

    await TestBed.configureTestingModule({
      imports: [ForgotPassword],
      providers: [provideRouter([]), { provide: AuthService, useValue: authService }]
    }).compileComponents();

    fixture = TestBed.createComponent(ForgotPassword);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should not call the service when email is invalid', () => {
    component.form.setValue({ email: 'nao-e-email' });

    component.submit();

    expect(authService.forgotPassword).not.toHaveBeenCalled();
  });

  it('should show a neutral confirmation when the request succeeds', () => {
    authService.forgotPassword.and.returnValue(of(void 0));
    component.form.setValue({ email: 'victor@poupito.com' });

    component.submit();

    expect(authService.forgotPassword).toHaveBeenCalledWith('victor@poupito.com');
    expect(component.sent()).toBeTrue();
    expect(component.errorMessage()).toBeNull();
  });

  it('should show a rate-limit message on 429', () => {
    authService.forgotPassword.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 429 }))
    );
    component.form.setValue({ email: 'victor@poupito.com' });

    component.submit();

    expect(component.sent()).toBeFalse();
    expect(component.errorMessage()).toContain('Muitas tentativas');
  });

  it('should show a generic message on server error', () => {
    authService.forgotPassword.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 500 }))
    );
    component.form.setValue({ email: 'victor@poupito.com' });

    component.submit();

    expect(component.errorMessage()).toContain('Erro ao comunicar');
  });
});
