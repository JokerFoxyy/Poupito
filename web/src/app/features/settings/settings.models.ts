export type AccountType = 'CHECKING' | 'CASH';
export type CategoryKind = 'EXPENSE' | 'INCOME';
export type PaymentMethod = 'CREDITO' | 'DEBITO' | 'DINHEIRO';

export interface Account {
  id: string;
  name: string;
  type: AccountType;
}

export interface Card {
  id: string;
  name: string;
  accountId: string;
  accountName: string | null;
  closingDay: number;
  dueDay: number;
  archived: boolean;
}

export interface Category {
  id: string;
  name: string;
  icon: string | null;
  color: string | null;
  kind: CategoryKind;
}

export const ACCOUNT_TYPE_LABELS: Record<AccountType, string> = {
  CHECKING: 'Conta corrente',
  CASH: 'Dinheiro'
};

export const CATEGORY_KIND_LABELS: Record<CategoryKind, string> = {
  EXPENSE: 'Gasto',
  INCOME: 'Entrada'
};

export const PAYMENT_METHOD_LABELS: Record<PaymentMethod, string> = {
  CREDITO: 'Crédito',
  DEBITO: 'Débito',
  DINHEIRO: 'Dinheiro'
};
