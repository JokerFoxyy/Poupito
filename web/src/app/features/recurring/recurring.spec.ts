import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { Recurring } from './recurring';
import { RecurringService } from './recurring.service';
import { AccountService } from '../settings/account.service';
import { CardService } from '../settings/card.service';
import { CategoryService } from '../settings/category.service';
import { Account, Card, Category } from '../settings/settings.models';
import { Occurrence, Recurring as RecurringModel } from './recurring.models';

describe('Recurring', () => {
  let fixture: ComponentFixture<Recurring>;
  let component: Recurring;
  let recurringService: jasmine.SpyObj<RecurringService>;

  const accounts: Account[] = [{ id: 'a1', name: 'Uniclass', type: 'CHECKING' }];
  const cards: Card[] = [
    { id: 'k1', name: 'Nubank', accountId: 'a1', accountName: 'Uniclass', closingDay: 3, dueDay: 10 }
  ];
  const categories: Category[] = [
    { id: 'c1', name: 'Assinaturas', icon: '🔁', color: '#a371f7', kind: 'EXPENSE' },
    { id: 'c2', name: 'Salário', icon: '💰', color: '#3fb950', kind: 'INCOME' }
  ];
  const spotify: RecurringModel = {
    id: 'r1', description: 'Spotify', amount: 27.9, type: 'EXPENSE', accountId: 'a1', accountName: 'Uniclass',
    cardId: null, cardName: null, method: 'DEBITO',
    categoryId: 'c1', categoryName: 'Assinaturas', categoryIcon: '🔁', categoryColor: '#a371f7',
    dayOfMonth: 10, active: true, endDate: null
  };
  const netflix: RecurringModel = {
    id: 'r2', description: 'Netflix', amount: 55.9, type: 'EXPENSE', accountId: null, accountName: null,
    cardId: 'k1', cardName: 'Nubank', method: 'CREDITO',
    categoryId: 'c1', categoryName: 'Assinaturas', categoryIcon: '🔁', categoryColor: '#a371f7',
    dayOfMonth: 10, active: true, endDate: null
  };
  const occurrence: Occurrence = {
    recurringId: 'r1', description: 'Spotify', amount: 27.9, type: 'EXPENSE', accountName: 'Uniclass',
    cardName: null, method: 'DEBITO',
    categoryName: 'Assinaturas', categoryIcon: '🔁', categoryColor: '#a371f7', dayOfMonth: 10,
    date: '2026-07-10', transactionId: 't1', materialized: true, paid: false
  };

  beforeEach(async () => {
    recurringService = jasmine.createSpyObj<RecurringService>('RecurringService',
      ['list', 'create', 'update', 'delete', 'occurrences', 'materialize', 'setPaid']);
    recurringService.list.and.returnValue(of([spotify]));
    recurringService.occurrences.and.returnValue(of([occurrence]));
    const accountService = jasmine.createSpyObj<AccountService>('AccountService', ['list']);
    accountService.list.and.returnValue(of(accounts));
    const cardService = jasmine.createSpyObj<CardService>('CardService', ['list']);
    cardService.list.and.returnValue(of(cards));
    const categoryService = jasmine.createSpyObj<CategoryService>('CategoryService', ['list']);
    categoryService.list.and.returnValue(of(categories));

    await TestBed.configureTestingModule({
      imports: [Recurring],
      providers: [
        { provide: RecurringService, useValue: recurringService },
        { provide: AccountService, useValue: accountService },
        { provide: CardService, useValue: cardService },
        { provide: CategoryService, useValue: categoryService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(Recurring);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should render the recurring list with category tag and paid checkbox', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Spotify');
    expect(fixture.nativeElement.querySelector('.tag').textContent).toContain('Assinaturas');
    expect(fixture.nativeElement.querySelector('.paid-check')).not.toBeNull();
  });

  it('should materialize the month occurrences', () => {
    const paidOccurrence = { ...occurrence, paid: true };
    recurringService.materialize.and.returnValue(of([paidOccurrence]));

    component.materialize();

    expect(recurringService.materialize).toHaveBeenCalledWith(component.month());
    expect(component.occurrences()[0].paid).toBeTrue();
  });

  it('should toggle paid via setPaid and reload occurrences', () => {
    recurringService.setPaid.and.returnValue(of({}));

    component.togglePaid(occurrence);

    expect(recurringService.setPaid).toHaveBeenCalledWith('t1', true);
    expect(recurringService.occurrences).toHaveBeenCalledTimes(2);
  });

  it('should not call setPaid when occurrence is not materialized', () => {
    component.togglePaid({ ...occurrence, transactionId: null, materialized: false });

    expect(recurringService.setPaid).not.toHaveBeenCalled();
  });

  it('should find the occurrence for a recurring', () => {
    expect(component.occurrenceFor('r1')).toEqual(occurrence);
    expect(component.occurrenceFor('nope')).toBeUndefined();
  });

  it('should switch category options when type changes to income', () => {
    component.openCreate();
    component.form.controls.type.setValue('INCOME');
    component.onTypeChange();

    expect(component.categoriesForType()).toEqual([categories[1]]);
    expect(component.form.controls.categoryId.value).toBe('c2');
  });

  it('should create a recurring when the form is valid', () => {
    recurringService.create.and.returnValue(of(spotify));
    component.openCreate();
    component.form.patchValue({ description: 'Academia', amount: 89.9, dayOfMonth: 5 });

    component.submit();

    expect(recurringService.create).toHaveBeenCalledWith(
      jasmine.objectContaining({ description: 'Academia', amount: 89.9, dayOfMonth: 5, active: true }));
    expect(component.showForm()).toBeFalse();
  });

  it('should not submit when the form is invalid', () => {
    component.openCreate();
    component.form.patchValue({ description: '' });

    component.submit();

    expect(recurringService.create).not.toHaveBeenCalled();
  });

  it('should update the recurring when editing', () => {
    recurringService.update.and.returnValue(of(spotify));

    component.openEdit(spotify);
    component.form.patchValue({ description: 'Spotify Family' });
    component.submit();

    expect(recurringService.update).toHaveBeenCalledWith('r1',
      jasmine.objectContaining({ description: 'Spotify Family' }));
  });

  it('should delete the recurring and reload', () => {
    recurringService.delete.and.returnValue(of(void 0));

    component.remove(spotify);

    expect(recurringService.delete).toHaveBeenCalledWith('r1');
    expect(recurringService.list).toHaveBeenCalledTimes(2);
  });

  it('should reload occurrences when the month changes', () => {
    component.onMonthChange('2026-06');

    expect(component.month()).toBe('2026-06');
    expect(recurringService.occurrences).toHaveBeenCalledWith('2026-06');
  });

  it('should default the target to the first account and send accountId', () => {
    recurringService.create.and.returnValue(of(spotify));
    component.openCreate();

    expect(component.form.controls.target.value).toBe('account:a1');
    expect(component.isCardSelected()).toBeFalse();

    component.form.patchValue({ description: 'Academia', amount: 89.9 });
    component.submit();

    expect(recurringService.create).toHaveBeenCalledWith(
      jasmine.objectContaining({ accountId: 'a1' }));
    expect(recurringService.create.calls.mostRecent().args[0].cardId).toBeUndefined();
  });

  it('should send cardId when a card is selected as target', () => {
    recurringService.create.and.returnValue(of(netflix));
    component.openCreate();
    component.form.patchValue({ description: 'Netflix', amount: 55.9, target: 'card:k1' });

    expect(component.isCardSelected()).toBeTrue();

    component.submit();

    expect(recurringService.create).toHaveBeenCalledWith(
      jasmine.objectContaining({ cardId: 'k1' }));
    expect(recurringService.create.calls.mostRecent().args[0].accountId).toBeUndefined();
  });

  it('should offer cards for expenses but hide them for income', () => {
    component.openCreate();
    expect(component.cardsForType()).toEqual(cards);

    component.form.controls.type.setValue('INCOME');
    expect(component.cardsForType()).toEqual([]);
  });

  it('should move the target back to an account when switching to income with a card selected', () => {
    component.openCreate();
    component.form.patchValue({ target: 'card:k1' });

    component.form.controls.type.setValue('INCOME');
    component.onTypeChange();

    expect(component.form.controls.target.value).toBe('account:a1');
    expect(component.isCardSelected()).toBeFalse();
  });

  it('should prefill the target with the card when editing a card-based recurring', () => {
    component.openEdit(netflix);

    expect(component.form.controls.target.value).toBe('card:k1');
    expect(component.isCardSelected()).toBeTrue();
  });

  it('should render the credit method badge and "na fatura" instead of the paid checkbox', () => {
    recurringService.list.and.returnValue(of([netflix]));
    recurringService.occurrences.and.returnValue(of([
      { ...occurrence, recurringId: 'r2', cardName: 'Nubank', method: 'CREDITO', accountName: null, paid: true }
    ]));
    component.ngOnInit();
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Nubank');
    expect(text).toContain('Crédito');
    expect(text).toContain('na fatura');
    expect(fixture.nativeElement.querySelector('.paid-check')).toBeNull();
    expect(fixture.nativeElement.querySelector('.method-credito')).not.toBeNull();
  });

  it('should total only the expense occurrences of the month', () => {
    recurringService.occurrences.and.returnValue(of([
      { ...occurrence, recurringId: 'r1', amount: 89.9, type: 'EXPENSE' },
      { ...occurrence, recurringId: 'r2', amount: 55.9, type: 'EXPENSE' },
      { ...occurrence, recurringId: 'r3', amount: 4000, type: 'INCOME' }
    ]));
    component.ngOnInit();

    expect(component.totalExpenses()).toBeCloseTo(145.8, 2);
    expect(component.totalIncome()).toBe(4000);
  });

  it('should ignore the paid flag when totalling the month', () => {
    recurringService.occurrences.and.returnValue(of([
      { ...occurrence, recurringId: 'r1', amount: 89.9, type: 'EXPENSE', paid: false },
      { ...occurrence, recurringId: 'r2', amount: 55.9, type: 'EXPENSE', paid: true }
    ]));
    component.ngOnInit();

    expect(component.totalExpenses()).toBeCloseTo(145.8, 2);
  });

  it('should render the summary with the monthly fixed expense total', () => {
    recurringService.occurrences.and.returnValue(of([
      { ...occurrence, amount: 89.9, type: 'EXPENSE', paid: false }
    ]));
    component.ngOnInit();
    fixture.detectChanges();

    const summary = fixture.nativeElement.querySelector('.fixed-summary');
    expect(summary).not.toBeNull();
    expect(summary.textContent).toContain('Gastos fixos do mês');
    // separador decimal varia com o locale (Karma roda en-US; o app, pt-BR)
    expect(summary.textContent).toMatch(/R\$\s?89[.,]90/);
  });

  it('should hide the summary when there is no occurrence in the month', () => {
    recurringService.occurrences.and.returnValue(of([]));
    component.ngOnInit();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.fixed-summary')).toBeNull();
    expect(component.totalExpenses()).toBe(0);
  });

  it('should omit the income line when there is no fixed income', () => {
    recurringService.occurrences.and.returnValue(of([
      { ...occurrence, amount: 89.9, type: 'EXPENSE', paid: true }
    ]));
    component.ngOnInit();
    fixture.detectChanges();

    const summary = fixture.nativeElement.querySelector('.fixed-summary');
    expect(summary.textContent).not.toContain('Entradas fixas');
    expect(summary.textContent).toContain('Gastos fixos do mês');
  });

  it('should show backend message when save fails', () => {
    recurringService.create.and.returnValue(
      throwError(() => ({ error: { message: 'Categoria não é compatível' } })));
    component.openCreate();
    component.form.patchValue({ description: 'X', amount: 10 });

    component.submit();

    expect(component.errorMessage()).toContain('Categoria não é compatível');
  });
});
