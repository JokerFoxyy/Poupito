import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { ResetPassword } from './reset-password';
import { AuthService } from '../../core/auth/auth.service';

describe('ResetPassword', () => {
  let authService: jasmine.SpyObj<AuthService>;

  const STRONG = 'NovaSenha-2026!';

  async function setup(token: string | null): Promise<ComponentFixture<ResetPassword>> {
    TestBed.resetTestingModule();
    authService = jasmine.createSpyObj<AuthService>('AuthService', ['resetPassword']);

    await TestBed.configureTestingModule({
      imports: [ResetPassword],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authService },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(token ? { token } : {}) } }
        }
      ]
    }).compileComponents();

    const fixture = TestBed.createComponent(ResetPassword);
    fixture.detectChanges();
    return fixture;
  }

  it('should flag missing token', async () => {
    const fixture = await setup(null);
    expect(fixture.componentInstance.hasToken).toBeFalse();
  });

  it('should not call the service when the password is weak', async () => {
    const fixture = await setup('tok-1');
    const component = fixture.componentInstance;
    component.form.setValue({ newPassword: 'fraca' });

    component.submit();

    expect(authService.resetPassword).not.toHaveBeenCalled();
  });

  it('should reset the password and mark done on success', async () => {
    const fixture = await setup('tok-1');
    const component = fixture.componentInstance;
    authService.resetPassword.and.returnValue(of(void 0));
    component.form.setValue({ newPassword: STRONG });

    component.submit();

    expect(authService.resetPassword).toHaveBeenCalledWith('tok-1', STRONG);
    expect(component.done()).toBeTrue();
  });

  it('should show an invalid-link message on 400', async () => {
    const fixture = await setup('tok-1');
    const component = fixture.componentInstance;
    authService.resetPassword.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 400 }))
    );
    component.form.setValue({ newPassword: STRONG });

    component.submit();

    expect(component.errorMessage()).toContain('Link inválido');
    expect(component.done()).toBeFalse();
  });

  it('should show a generic message on server error', async () => {
    const fixture = await setup('tok-1');
    const component = fixture.componentInstance;
    authService.resetPassword.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 500 }))
    );
    component.form.setValue({ newPassword: STRONG });

    component.submit();

    expect(component.errorMessage()).toContain('Erro ao comunicar');
  });

  it('should toggle password visibility', async () => {
    const fixture = await setup('tok-1');
    const component = fixture.componentInstance;

    component.toggleShowPassword();

    expect(component.showPassword()).toBeTrue();
  });
});
