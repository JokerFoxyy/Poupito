import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { Login } from './login';
import { AuthService } from '../../core/auth/auth.service';
import { UserResponse } from '../../core/auth/auth.models';

describe('Login', () => {
  let fixture: ComponentFixture<Login>;
  let component: Login;
  let authService: jasmine.SpyObj<AuthService>;
  let router: Router;

  const user: UserResponse = { id: 'u1', email: 'victor@poupito.com' };
  const STRONG = 'Senha-Forte-123';

  function fillForm(email: string, password: string): void {
    component.form.setValue({ email, password });
  }

  beforeEach(async () => {
    authService = jasmine.createSpyObj<AuthService>('AuthService', ['login', 'register']);

    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authService }
      ]
    }).compileComponents();

    router = TestBed.inject(Router);
    spyOn(router, 'navigate');

    fixture = TestBed.createComponent(Login);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create with login mode by default', () => {
    expect(component.mode()).toBe('login');
  });

  it('should not call the service when form is invalid', () => {
    fillForm('nao-e-email', '');

    component.submit();

    expect(authService.login).not.toHaveBeenCalled();
    expect(authService.register).not.toHaveBeenCalled();
  });

  it('should navigate to dashboard when login succeeds', () => {
    authService.login.and.returnValue(of(user));
    fillForm('victor@poupito.com', 'qualquer-senha');

    component.submit();

    expect(authService.login).toHaveBeenCalledWith('victor@poupito.com', 'qualquer-senha');
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
  });

  it('should call register when in register mode with a strong password', () => {
    authService.register.and.returnValue(of(user));
    component.toggleMode();
    fillForm('novo@poupito.com', STRONG);

    component.submit();

    expect(authService.register).toHaveBeenCalledWith('novo@poupito.com', STRONG);
  });

  it('should not call register when password is too weak in register mode', () => {
    component.toggleMode();
    fillForm('novo@poupito.com', 'fraca');

    component.submit();

    expect(authService.register).not.toHaveBeenCalled();
  });

  it('should keep login accepting a legacy-format password (only required)', () => {
    authService.login.and.returnValue(of(user));
    fillForm('victor@poupito.com', 'senha10chars');

    component.submit();

    expect(authService.login).toHaveBeenCalled();
  });

  it('should show invalid credentials message when login returns 401', () => {
    authService.login.and.returnValue(throwError(() => new HttpErrorResponse({ status: 401 })));
    fillForm('victor@poupito.com', 'senha-errada');

    component.submit();

    expect(component.errorMessage()).toBe('Email ou senha inválidos');
    expect(component.loading()).toBeFalse();
  });

  it('should show duplicate email message when register returns 409', () => {
    authService.register.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    component.toggleMode();
    fillForm('duplicado@poupito.com', STRONG);

    component.submit();

    expect(component.errorMessage()).toBe('Email já cadastrado');
  });

  it('should show lockout message with remaining minutes when login returns 429', () => {
    authService.login.and.returnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 429,
            headers: new HttpHeaders({ 'Retry-After': '120' })
          })
      )
    );
    fillForm('victor@poupito.com', 'senha-errada');

    component.submit();

    expect(component.errorMessage()).toBe('Muitas tentativas. Tente novamente em 2 min.');
  });

  it('should show a generic lockout message when 429 has no Retry-After', () => {
    authService.login.and.returnValue(throwError(() => new HttpErrorResponse({ status: 429 })));
    fillForm('victor@poupito.com', 'senha-errada');

    component.submit();

    expect(component.errorMessage()).toContain('Muitas tentativas');
  });

  it('should show generic message when server fails', () => {
    authService.login.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    fillForm('victor@poupito.com', 'qualquer-senha');

    component.submit();

    expect(component.errorMessage()).toContain('Erro ao comunicar com o servidor');
  });

  it('should toggle password visibility', () => {
    expect(component.showPassword()).toBeFalse();

    component.toggleShowPassword();

    expect(component.showPassword()).toBeTrue();
  });

  it('should clear error message when toggling mode', () => {
    authService.login.and.returnValue(throwError(() => new HttpErrorResponse({ status: 401 })));
    fillForm('victor@poupito.com', 'senha-errada');
    component.submit();

    component.toggleMode();

    expect(component.mode()).toBe('register');
    expect(component.errorMessage()).toBeNull();
  });
});
