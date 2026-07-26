import { FormControl } from '@angular/forms';

import { PASSWORD_POLICY_HINT, strongPassword } from './password.validator';

describe('strongPassword', () => {
  function check(value: string) {
    return strongPassword(new FormControl(value));
  }

  it('should accept a password meeting all rules', () => {
    expect(check('NovaSenha-2026')).toBeNull();
  });

  it('should reject an empty value', () => {
    expect(check('')).toEqual({ weakPassword: true });
  });

  it('should reject when too short', () => {
    expect(check('Ab1!xyz')).toEqual({ weakPassword: true });
  });

  it('should reject when missing uppercase', () => {
    expect(check('novasenha-2026')).toEqual({ weakPassword: true });
  });

  it('should reject when missing lowercase', () => {
    expect(check('NOVASENHA-2026')).toEqual({ weakPassword: true });
  });

  it('should reject when missing digit', () => {
    expect(check('NovaSenha-Poup')).toEqual({ weakPassword: true });
  });

  it('should reject when missing symbol', () => {
    expect(check('NovaSenha2026x')).toEqual({ weakPassword: true });
  });

  it('should expose the policy hint text', () => {
    expect(PASSWORD_POLICY_HINT).toContain('12 caracteres');
  });
});
