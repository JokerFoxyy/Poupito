import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { AccountsPanel } from './accounts-panel';
import { AccountService } from './account.service';
import { Account } from './settings.models';

describe('AccountsPanel', () => {
  let fixture: ComponentFixture<AccountsPanel>;
  let component: AccountsPanel;
  let accountService: jasmine.SpyObj<AccountService>;

  const nubank: Account = { id: '1', name: 'Nubank', type: 'CHECKING' };
  const carteira: Account = { id: '2', name: 'Carteira', type: 'CASH' };

  beforeEach(async () => {
    accountService = jasmine.createSpyObj<AccountService>('AccountService', ['list', 'create', 'update', 'delete']);
    accountService.list.and.returnValue(of([nubank, carteira]));

    await TestBed.configureTestingModule({
      imports: [AccountsPanel],
      providers: [{ provide: AccountService, useValue: accountService }]
    }).compileComponents();

    fixture = TestBed.createComponent(AccountsPanel);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should list accounts on init with pt-BR type labels', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Nubank');
    expect(text).toContain('Conta corrente');
    expect(text).toContain('Dinheiro');
  });

  it('should create an account when form is valid', () => {
    accountService.create.and.returnValue(of(carteira));

    component.openCreate();
    component.form.setValue({ name: 'Carteira', type: 'CASH' });
    component.submit();

    expect(accountService.create).toHaveBeenCalledWith(
      jasmine.objectContaining({ name: 'Carteira', type: 'CASH' })
    );
    expect(component.showForm()).toBeFalse();
    expect(accountService.list).toHaveBeenCalledTimes(2);
  });

  it('should not call the service when name is empty', () => {
    component.openCreate();
    component.form.setValue({ name: '', type: 'CHECKING' });

    component.submit();

    expect(accountService.create).not.toHaveBeenCalled();
  });

  it('should update the account when editing', () => {
    accountService.update.and.returnValue(of(nubank));

    component.openEdit(nubank);
    component.form.patchValue({ name: 'Nubank UV' });
    component.submit();

    expect(accountService.update).toHaveBeenCalledWith('1', jasmine.objectContaining({ name: 'Nubank UV' }));
  });

  it('should delete the account and reload the list', () => {
    accountService.delete.and.returnValue(of(void 0));

    component.remove(nubank);

    expect(accountService.delete).toHaveBeenCalledWith('1');
    expect(accountService.list).toHaveBeenCalledTimes(2);
  });

  it('should show error message when save fails', () => {
    accountService.create.and.returnValue(throwError(() => new Error('500')));

    component.openCreate();
    component.form.setValue({ name: 'Carteira', type: 'CASH' });
    component.submit();

    expect(component.errorMessage()).toContain('Erro ao salvar');
  });

  it('should surface the server message when the name is duplicated (409)', () => {
    accountService.create.and.returnValue(
      throwError(() => ({ status: 409, error: { message: 'Você já tem uma conta com esse nome' } }))
    );

    component.openCreate();
    component.form.setValue({ name: 'Nubank', type: 'CHECKING' });
    component.submit();

    expect(component.errorMessage()).toBe('Você já tem uma conta com esse nome');
  });

  it('should fall back to a default duplicate message when 409 has no body', () => {
    accountService.create.and.returnValue(throwError(() => ({ status: 409 })));

    component.openCreate();
    component.form.setValue({ name: 'Nubank', type: 'CHECKING' });
    component.submit();

    expect(component.errorMessage()).toContain('já tem uma conta com esse nome');
  });
});
