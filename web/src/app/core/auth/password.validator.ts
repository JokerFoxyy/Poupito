import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/** Texto de política mostrado só onde se DEFINE senha (cadastro/redefinição), nunca no login. */
export const PASSWORD_POLICY_HINT =
  'Use ao menos 12 caracteres, com maiúscula, minúscula, número e símbolo.';

const MIN_LENGTH = 12;
const MAX_LENGTH = 100;

/**
 * Validador de senha forte — espelha o `@StrongPassword` do backend (sessão #29):
 * ≥12 caracteres, com maiúscula, minúscula, número e símbolo.
 */
export const strongPassword: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value = control.value as string;
  if (!value) {
    return { weakPassword: true };
  }
  const longEnough = value.length >= MIN_LENGTH && value.length <= MAX_LENGTH;
  const hasUpper = /[A-Z]/.test(value);
  const hasLower = /[a-z]/.test(value);
  const hasDigit = /\d/.test(value);
  const hasSymbol = /[^A-Za-z0-9\s]/.test(value);
  return longEnough && hasUpper && hasLower && hasDigit && hasSymbol ? null : { weakPassword: true };
};
