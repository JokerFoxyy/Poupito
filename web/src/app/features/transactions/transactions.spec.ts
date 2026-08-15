import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';

import { Transactions } from './transactions';
import { TransactionService } from './transaction.service';
import { AccountService } from '../settings/account.service';
import { CardService } from '../settings/card.service';
import { CategoryService } from '../settings/category.service';
import { PageResponse, Transaction } from './transaction.models';
import { Account, Card, Category } from '../settings/settings.models';

describe('Transactions', () => {
  let fixture: ComponentFixture<Transactions>;
  let component: Transactions;
  let transactionService: jasmine.SpyObj<TransactionService>;

  const accounts: Account[] = [
    { id: 'a1', name: 'Uniclass', type: 'CHECKING' },
    { id: 'a2', name: 'Carteira', type: 'CASH' }
  ];
  const cards: Card[] = [
    { id: 'k1', name: 'Nubank', accountId: 'a1', accountName: 'Uniclass', closingDay: 28, dueDay: 7, archived: false }
  ];
  const categories: Category[] = [
    { id: 'c1', name: 'Mercado', icon: '🛒', color: '#3fb950', kind: 'EXPENSE' },
    { id: 'c2', name: 'Salário', icon: '💰', color: '#d29922', kind: 'INCOME' }
  ];
  const padaria: Transaction = {
    id: 't1', description: 'Padaria', amount: 31.73, date: '2026-07-09', type: 'EXPENSE',
    accountId: 'a1', accountName: 'Uniclass', cardId: null, cardName: null, method: 'DEBITO',
    categoryId: 'c1', categoryName: 'Mercado', categoryIcon: '🛒', categoryColor: '#3fb950',
    invoiceMonth: null, tags: ['viagem', 'trabalho'], installmentNumber: null, installmentCount: null
  };
  const cartao: Transaction = {
    ...padaria, id: 't2', description: 'Streaming', accountId: null, accountName: null,
    cardId: 'k1', cardName: 'Nubank', method: 'CREDITO', invoiceMonth: '2026-08-01'
  };
  const parcela: Transaction = {
    ...cartao, id: 't3', description: 'Notebook', installmentNumber: 2, installmentCount: 6
  };

  function pageOf(items: Transaction[], totalPages = 1): PageResponse<Transaction> {
    return { content: items, page: 0, size: 50, totalElements: items.length, totalPages };
  }

  beforeEach(async () => {
    localStorage.removeItem('poupito.lastPaymentTarget');
    transactionService = jasmine.createSpyObj<TransactionService>('TransactionService',
      ['list', 'create', 'update', 'delete', 'export']);
    transactionService.list.and.returnValue(of(pageOf([padaria, cartao])));
    const accountService = jasmine.createSpyObj<AccountService>('AccountService', ['list']);
    accountService.list.and.returnValue(of(accounts));
    const cardService = jasmine.createSpyObj<CardService>('CardService', ['list']);
    cardService.list.and.returnValue(of(cards));
    const categoryService = jasmine.createSpyObj<CategoryService>('CategoryService', ['list']);
    categoryService.list.and.returnValue(of(categories));

    await TestBed.configureTestingModule({
      imports: [Transactions],
      providers: [
        { provide: TransactionService, useValue: transactionService },
        { provide: AccountService, useValue: accountService },
        { provide: CardService, useValue: cardService },
        { provide: CategoryService, useValue: categoryService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(Transactions);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => localStorage.removeItem('poupito.lastPaymentTarget'));

  it('should render transactions with category tag and invoice hint', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Padaria');
    expect(fixture.nativeElement.querySelector('.tag').textContent).toContain('Mercado');
    expect(fixture.nativeElement.querySelector('.invoice-hint').textContent).toContain('fatura');
  });

  it('should render the payment method badge for each transaction', () => {
    const badges = fixture.nativeElement.querySelectorAll('.method-badge');
    const labels = Array.from(badges).map((b) => (b as HTMLElement).textContent?.trim());
    expect(labels).toContain('Débito');
    expect(labels).toContain('Crédito');
  });

  it('should show the card name in the account/card column', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Nubank');
  });

  it('should render the tag chips of a transaction', () => {
    const chips = fixture.nativeElement.querySelectorAll('.tag-chip');
    expect(chips.length).toBeGreaterThanOrEqual(2);
    expect(chips[0].textContent).toContain('viagem');
  });

  it('should open create modal with today and first account as defaults', () => {
    component.openCreate();

    const now = new Date();
    const todayLocal = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
    expect(component.modalOpen()).toBeTrue();
    expect(component.form.controls.date.value).toBe(todayLocal);
    expect(component.form.controls.target.value).toBe('account:a1');
    expect(component.form.controls.categoryId.value).toBe('c1');
  });

  it('should prefer the last used payment target when opening create modal', () => {
    localStorage.setItem('poupito.lastPaymentTarget', 'card:k1');

    component.openCreate();

    expect(component.form.controls.target.value).toBe('card:k1');
  });

  it('should create an account-backed transaction and remember the target', () => {
    transactionService.create.and.returnValue(of(padaria));
    component.openCreate();
    component.form.patchValue({ description: 'Padaria', amount: 31.73 });

    component.submit();

    expect(transactionService.create).toHaveBeenCalledWith(
      jasmine.objectContaining({ description: 'Padaria', amount: 31.73, accountId: 'a1' }));
    const payload = transactionService.create.calls.mostRecent().args[0];
    expect(payload.cardId).toBeUndefined();
    expect(localStorage.getItem('poupito.lastPaymentTarget')).toBe('account:a1');
    expect(component.modalOpen()).toBeFalse();
    expect(transactionService.list).toHaveBeenCalledTimes(2);
  });

  it('should create a card-backed transaction with cardId when a card is selected', () => {
    transactionService.create.and.returnValue(of(cartao));
    component.openCreate();
    component.form.patchValue({ description: 'Streaming', amount: 39.9, target: 'card:k1' });

    component.submit();

    expect(transactionService.create).toHaveBeenCalledWith(
      jasmine.objectContaining({ cardId: 'k1' }));
    const payload = transactionService.create.calls.mostRecent().args[0];
    expect(payload.accountId).toBeUndefined();
  });

  it('should not call the service when form is invalid', () => {
    component.openCreate();
    component.form.patchValue({ description: '' });

    component.submit();

    expect(transactionService.create).not.toHaveBeenCalled();
  });

  it('should switch category options when type changes to income', () => {
    component.openCreate();

    component.form.controls.type.setValue('INCOME');
    component.onTypeChange();

    expect(component.categoriesForType()).toEqual([categories[1]]);
    expect(component.form.controls.categoryId.value).toBe('c2');
  });

  it('should move a card selection back to an account when switching to income', () => {
    component.openCreate();
    component.form.patchValue({ target: 'card:k1', type: 'INCOME' });

    component.onTypeChange();

    expect(component.form.controls.target.value).toBe('account:a1');
    expect(component.isCardSelected()).toBeFalse();
  });

  it('should hide cards from the payment selector for income', () => {
    component.openCreate();
    component.form.controls.type.setValue('INCOME');

    expect(component.cardsForType()).toEqual([]);
  });

  it('should update the transaction when editing', () => {
    transactionService.update.and.returnValue(of(padaria));

    component.openEdit(padaria);
    component.form.patchValue({ description: 'Padaria Sameiro' });
    component.submit();

    expect(transactionService.update).toHaveBeenCalledWith('t1',
      jasmine.objectContaining({ description: 'Padaria Sameiro' }));
  });

  it('should set the target to the card when editing a card transaction', () => {
    component.openEdit(cartao);

    expect(component.form.controls.target.value).toBe('card:k1');
  });

  it('should prefill the tags field with a comma-separated list when editing', () => {
    component.openEdit(padaria);

    expect(component.form.controls.tags.value).toBe('viagem, trabalho');
  });

  it('should parse the tags field into a trimmed string array on submit', () => {
    transactionService.create.and.returnValue(of(padaria));
    component.openCreate();
    component.form.patchValue({ description: 'Uber', amount: 52.53, tags: ' viagem ,  trabalho,,' });

    component.submit();

    expect(transactionService.create).toHaveBeenCalledWith(
      jasmine.objectContaining({ tags: ['viagem', 'trabalho'] }));
  });

  it('should not open edit modal for invoice adjustments', () => {
    component.openEdit({ ...padaria, type: 'INVOICE_ADJUSTMENT' });

    expect(component.modalOpen()).toBeFalse();
  });

  it('should not open edit modal for invoice payments', () => {
    component.openEdit({ ...padaria, type: 'INVOICE_PAYMENT' });

    expect(component.modalOpen()).toBeFalse();
  });

  it('should delete a transaction and reload', () => {
    transactionService.delete.and.returnValue(of(void 0));

    component.remove(padaria);

    expect(transactionService.delete).toHaveBeenCalledWith('t1', undefined);
    expect(transactionService.list).toHaveBeenCalledTimes(2);
  });

  it('should delete the whole future group when scope is group', () => {
    transactionService.delete.and.returnValue(of(void 0));

    component.remove(parcela, 'group');

    expect(transactionService.delete).toHaveBeenCalledWith('t3', 'group');
  });

  it('should reload from page zero when month changes', () => {
    component.goToPage(2);

    component.onMonthChange('2026-06');

    expect(component.page()).toBe(0);
    expect(transactionService.list).toHaveBeenCalledWith('2026-06', jasmine.anything(), 0);
  });

  it('should pass the account filter to the service when the target is an account', () => {
    component.filterForm.patchValue({ target: 'account:a2', type: 'EXPENSE' });

    component.onFiltersChange();

    expect(transactionService.list).toHaveBeenCalledWith(jasmine.any(String),
      jasmine.objectContaining({ accountId: 'a2', type: 'EXPENSE' }), 0);
  });

  it('should pass the card filter to the service when the target is a card', () => {
    component.filterForm.patchValue({ target: 'card:k1' });

    component.onFiltersChange();

    expect(transactionService.list).toHaveBeenCalledWith(jasmine.any(String),
      jasmine.objectContaining({ cardId: 'k1' }), 0);
  });

  it('should debounce the free-text search before reloading', (done) => {
    transactionService.list.calls.reset();
    component.filterForm.patchValue({ q: 'padaria' });

    component.onSearchInput();

    expect(transactionService.list).not.toHaveBeenCalled();
    setTimeout(() => {
      expect(transactionService.list).toHaveBeenCalledWith(jasmine.any(String),
        jasmine.objectContaining({ q: 'padaria' }), 0);
      done();
    }, 350);
  });

  it('should render the installment badge when the transaction is part of a group', () => {
    transactionService.list.and.returnValue(of(pageOf([parcela])));
    component['load']();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.installment-chip').textContent).toContain('2/6');
  });

  it('should not send installments when the target is an account', () => {
    transactionService.create.and.returnValue(of(padaria));
    component.openCreate();
    component.form.patchValue({ description: 'Padaria', amount: 31.73, target: 'account:a1', installments: 6 });

    component.submit();

    const payload = transactionService.create.calls.mostRecent().args[0];
    expect(payload.installments).toBeUndefined();
  });

  it('should send installments when creating a parceled card transaction', () => {
    transactionService.create.and.returnValue(of(cartao));
    component.openCreate();
    component.form.patchValue({ description: 'Notebook', amount: 500, target: 'card:k1', installments: 6 });

    component.submit();

    expect(transactionService.create).toHaveBeenCalledWith(
      jasmine.objectContaining({ installments: 6, cardId: 'k1' }));
  });

  it('should not send installments when editing, even if the field has a value greater than one', () => {
    transactionService.update.and.returnValue(of(cartao));
    component.openEdit(cartao);
    component.form.patchValue({ installments: 6 });

    component.submit();

    const payload = transactionService.update.calls.mostRecent().args[1];
    expect(payload.installments).toBeUndefined();
  });

  it('should show the installments preview only when creating with more than one installment', () => {
    component.openCreate();
    component.form.patchValue({ amount: 100, installments: 3 });
    expect(component.installmentsPreview()).toContain('3x de');

    component.form.patchValue({ installments: 1 });
    expect(component.installmentsPreview()).toBeNull();
  });

  it('should not show the installments preview when editing', () => {
    component.openEdit(padaria);
    component.form.patchValue({ amount: 100, installments: 3 });

    expect(component.installmentsPreview()).toBeNull();
  });

  it('should export with the current month and filters', () => {
    transactionService.export.and.returnValue(of(new Blob(['data'])));
    spyOn(URL, 'createObjectURL').and.returnValue('blob:fake-url');
    spyOn(URL, 'revokeObjectURL');
    spyOn(HTMLAnchorElement.prototype, 'click');
    component.filterForm.patchValue({ tag: 'viagem' });

    component.export('xlsx');

    expect(transactionService.export).toHaveBeenCalledWith(
      jasmine.any(String), jasmine.objectContaining({ tag: 'viagem' }), 'xlsx');
    expect(HTMLAnchorElement.prototype.click).toHaveBeenCalled();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:fake-url');
  });

  it('should show an error message when export fails', () => {
    transactionService.export.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

    component.export('csv');

    expect(component.errorMessage()).toContain('exportar');
  });

  it('should show backend message when save fails', () => {
    transactionService.create.and.returnValue(throwError(() =>
      new HttpErrorResponse({ status: 400, error: { message: 'Categoria de entrada não pode ser usada' } })));
    component.openCreate();
    component.form.patchValue({ description: 'X', amount: 10 });

    component.submit();

    expect(component.errorMessage()).toContain('Categoria de entrada');
  });

  it('should warn and re-sync when the chosen account/card 404s (apagado noutra tela)', () => {
    transactionService.create.and.returnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    component.openCreate();
    component.form.patchValue({ description: 'X', amount: 10 });

    component.submit();

    expect(component.errorMessage()).toContain('não existe mais');
  });
});
