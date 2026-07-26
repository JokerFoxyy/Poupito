import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { CardsPanel } from './cards-panel';
import { CardService } from './card.service';
import { AccountService } from './account.service';
import { Account, Card } from './settings.models';

describe('CardsPanel', () => {
  let fixture: ComponentFixture<CardsPanel>;
  let component: CardsPanel;
  let cardService: jasmine.SpyObj<CardService>;
  let accountService: jasmine.SpyObj<AccountService>;

  const conta: Account = { id: 'a1', name: 'Nubank Conta', type: 'CHECKING' };
  const nubank: Card = { id: '1', name: 'Nubank', accountId: 'a1', accountName: 'Nubank Conta', closingDay: 28, dueDay: 7 };

  beforeEach(async () => {
    cardService = jasmine.createSpyObj<CardService>('CardService', ['list', 'create', 'update', 'delete']);
    accountService = jasmine.createSpyObj<AccountService>('AccountService', ['list']);
    cardService.list.and.returnValue(of([nubank]));
    accountService.list.and.returnValue(of([conta]));

    await TestBed.configureTestingModule({
      imports: [CardsPanel],
      providers: [
        { provide: CardService, useValue: cardService },
        { provide: AccountService, useValue: accountService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(CardsPanel);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should list cards with the linked account name', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Nubank');
    expect(text).toContain('Nubank Conta');
    expect(text).toContain('fecha dia 28');
  });

  it('should create a card when form is valid', () => {
    cardService.create.and.returnValue(of(nubank));

    component.openCreate();
    component.form.setValue({ name: 'Nubank UV', accountId: 'a1', closingDay: 28, dueDay: 7 });
    component.submit();

    expect(cardService.create).toHaveBeenCalledWith(
      jasmine.objectContaining({ name: 'Nubank UV', accountId: 'a1', closingDay: 28, dueDay: 7 })
    );
    expect(component.showForm()).toBeFalse();
  });

  it('should not call the service when required fields are missing', () => {
    component.openCreate();
    component.form.setValue({ name: '', accountId: '', closingDay: null, dueDay: null });

    component.submit();

    expect(cardService.create).not.toHaveBeenCalled();
  });

  it('should update the card when editing', () => {
    cardService.update.and.returnValue(of(nubank));

    component.openEdit(nubank);
    component.form.patchValue({ name: 'Nubank Ultravioleta' });
    component.submit();

    expect(cardService.update).toHaveBeenCalledWith('1', jasmine.objectContaining({ name: 'Nubank Ultravioleta' }));
  });

  it('should delete the card and reload the list', () => {
    cardService.delete.and.returnValue(of(void 0));

    component.remove(nubank);

    expect(cardService.delete).toHaveBeenCalledWith('1');
    expect(cardService.list).toHaveBeenCalledTimes(2);
  });

  it('should show error message when save fails', () => {
    cardService.create.and.returnValue(throwError(() => new Error('500')));

    component.openCreate();
    component.form.setValue({ name: 'Black', accountId: 'a1', closingDay: 1, dueDay: 10 });
    component.submit();

    expect(component.errorMessage()).toContain('Erro ao salvar');
  });

  it('should refresh accounts and warn when the linked account 404s (conta apagada noutra tela)', () => {
    cardService.create.and.returnValue(throwError(() => ({ status: 404 })));
    accountService.list.calls.reset();

    component.openCreate();
    component.form.setValue({ name: 'Black', accountId: 'ghost', closingDay: 1, dueDay: 10 });
    component.submit();

    expect(component.errorMessage()).toContain('não existe mais');
    expect(accountService.list).toHaveBeenCalled();
  });

  it('should surface the server message when the card name is duplicated (409)', () => {
    cardService.create.and.returnValue(
      throwError(() => ({ status: 409, error: { message: 'Você já tem um cartão com esse nome' } }))
    );

    component.openCreate();
    component.form.setValue({ name: 'Nubank', accountId: 'a1', closingDay: 1, dueDay: 10 });
    component.submit();

    expect(component.errorMessage()).toBe('Você já tem um cartão com esse nome');
  });

  it('should fall back to a default duplicate message when 409 has no body', () => {
    cardService.create.and.returnValue(throwError(() => ({ status: 409 })));

    component.openCreate();
    component.form.setValue({ name: 'Nubank', accountId: 'a1', closingDay: 1, dueDay: 10 });
    component.submit();

    expect(component.errorMessage()).toContain('já tem um cartão com esse nome');
  });
});
