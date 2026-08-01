import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { redirectIfAuthenticatedGuard } from './redirect-if-authenticated.guard';
import { AuthService } from './auth.service';

describe('redirectIfAuthenticatedGuard', () => {
  const route = {} as ActivatedRouteSnapshot;
  const state = {} as RouterStateSnapshot;

  beforeEach(() => {
    localStorage.removeItem('poupito.authed');
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
  });

  afterEach(() => {
    localStorage.removeItem('poupito.authed');
  });

  function runGuard(): boolean | UrlTree {
    return TestBed.runInInjectionContext(() => redirectIfAuthenticatedGuard(route, state)) as boolean | UrlTree;
  }

  it('should allow activation when not authenticated', () => {
    expect(runGuard()).toBeTrue();
  });

  it('should redirect to /dashboard when already authenticated', () => {
    const authService = TestBed.inject(AuthService);
    spyOn(authService, 'isAuthenticated').and.returnValue(true);
    const router = TestBed.inject(Router);

    const result = runGuard();

    expect(result instanceof UrlTree).toBeTrue();
    expect(router.serializeUrl(result as UrlTree)).toBe('/dashboard');
  });
});
