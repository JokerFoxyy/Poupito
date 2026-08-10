import { PaymentMethod } from '../settings/settings.models';

export type RecurringType = 'EXPENSE' | 'INCOME';

export interface Recurring {
  id: string;
  description: string;
  amount: number;
  type: RecurringType;
  /** Conta XOR cartão (sessão #32): exatamente um dos dois é preenchido. */
  accountId: string | null;
  accountName: string | null;
  cardId: string | null;
  cardName: string | null;
  method: PaymentMethod;
  categoryId: string;
  categoryName: string | null;
  categoryIcon: string | null;
  categoryColor: string | null;
  dayOfMonth: number;
  active: boolean;
  endDate: string | null;
  archived: boolean;
}

export interface RecurringPayload {
  description: string;
  amount: number;
  type: RecurringType;
  /** Informe accountId OU cardId — nunca os dois (o backend rejeita com 400). */
  accountId?: string;
  cardId?: string;
  categoryId: string;
  dayOfMonth: number;
  active: boolean;
  endDate: string | null;
}

export interface Occurrence {
  recurringId: string;
  description: string;
  amount: number;
  type: RecurringType;
  accountName: string | null;
  cardName: string | null;
  method: PaymentMethod;
  categoryName: string | null;
  categoryIcon: string | null;
  categoryColor: string | null;
  dayOfMonth: number;
  date: string;
  transactionId: string | null;
  materialized: boolean;
  paid: boolean;
}
