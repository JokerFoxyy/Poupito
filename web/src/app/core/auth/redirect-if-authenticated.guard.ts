import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from './auth.service';

/**
 * Inverso do authGuard: usado na landing pública (sessão #40) — quem já está
 * logado não deveria ver a landing de novo, e sim ir direto pro dashboard.
 */
export const redirectIfAuthenticatedGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);
  return authService.isAuthenticated() ? router.createUrlTree(['/dashboard']) : true;
};
