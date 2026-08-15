import { Routes } from '@angular/router';

import { authGuard } from './core/auth/auth.guard';
import { redirectIfAuthenticatedGuard } from './core/auth/redirect-if-authenticated.guard';
import { Shell } from './core/layout/shell';

export const routes: Routes = [
  {
    // pathMatch: 'full' é essencial — sem isso, este path vazio faria prefix
    // match e "roubaria" /dashboard, /transacoes etc. do Shell abaixo (sessão #40).
    path: '',
    pathMatch: 'full',
    canActivate: [redirectIfAuthenticatedGuard],
    loadComponent: () => import('./features/landing/landing').then((m) => m.Landing)
  },
  {
    path: 'faq',
    loadComponent: () => import('./features/faq/faq').then((m) => m.Faq)
  },
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login').then((m) => m.Login)
  },
  {
    path: 'esqueci-senha',
    loadComponent: () => import('./features/auth/forgot-password').then((m) => m.ForgotPassword)
  },
  {
    path: 'redefinir-senha',
    loadComponent: () => import('./features/auth/reset-password').then((m) => m.ResetPassword)
  },
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard)
      },
      {
        path: 'transacoes',
        loadComponent: () => import('./features/transactions/transactions').then((m) => m.Transactions)
      },
      {
        path: 'faturas',
        loadComponent: () => import('./features/invoices/invoices').then((m) => m.Invoices)
      },
      {
        path: 'investimentos',
        loadComponent: () => import('./features/investments/investments').then((m) => m.Investments)
      },
      {
        path: 'metas',
        loadComponent: () => import('./features/goals/goals').then((m) => m.Goals)
      },
      {
        path: 'fixos',
        loadComponent: () => import('./features/recurring/recurring').then((m) => m.Recurring)
      },
      {
        path: 'orcamentos',
        loadComponent: () => import('./features/budgets/budgets').then((m) => m.Budgets)
      },
      {
        path: 'importar',
        loadComponent: () => import('./features/importer/importer').then((m) => m.Importer)
      },
      {
        path: 'configuracoes',
        loadComponent: () => import('./features/settings/settings').then((m) => m.Settings)
      }
    ]
  },
  { path: '**', redirectTo: '' }
];
