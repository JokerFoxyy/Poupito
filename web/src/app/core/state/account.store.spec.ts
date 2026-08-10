import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { AccountStore } from './account.store';
import { AccountService } from '../../features/settings/account.service';
import { Account } from '../../features/settings/settings.models';

describe('AccountStore', () => {
  let store: AccountStore;
  let service: jasmine.SpyObj<AccountService>;

  const nubank: Account = { id: '1', name: 'Nubank', type: 'CHECKING' };
  const carteira: Account = { id: '2', name: 'Carteira', type: 'CASH' };

  beforeEach(() => {
    service = jasmine.createSpyObj<AccountService>('AccountService', ['list', 'create', 'update', 'delete']);
    service.list.and.returnValue(of([nubank, carteira]));

    TestBed.configureTestingModule({
      providers: [{ provide: AccountService, useValue: service }]
    });
    store = TestBed.inject(AccountStore);
  });

  it('should load accounts once, even when ensureLoaded is called by several consumers', () => {
    store.ensureLoaded();
    store.ensureLoaded();
    store.ensureLoaded();

    expect(service.list).toHaveBeenCalledTimes(1);
    expect(store.accounts()).toEqual([nubank, carteira]);
  });

  it('should re-fetch on refresh', () => {
    store.ensureLoaded();
    service.list.and.returnValue(of([nubank]));

    store.refresh();

    expect(store.accounts()).toEqual([nubank]);
  });

  it('should refresh the shared signal after creating', () => {
    store.ensureLoaded();
    service.create.and.returnValue(of(carteira));
    service.list.and.returnValue(of([nubank, carteira]));

    store.create({ name: 'Carteira', type: 'CASH' }).subscribe();

    expect(service.create).toHaveBeenCalled();
    expect(store.accounts()).toEqual([nubank, carteira]);
  });

  it('should refresh the shared signal after updating', () => {
    store.ensureLoaded();
    service.update.and.returnValue(of({ ...nubank, name: 'Nubank UV' }));
    service.list.and.returnValue(of([{ ...nubank, name: 'Nubank UV' }, carteira]));

    store.update('1', { name: 'Nubank UV', type: 'CHECKING' }).subscribe();

    expect(service.update).toHaveBeenCalledWith('1', jasmine.objectContaining({ name: 'Nubank UV' }));
    expect(store.accounts()[0].name).toBe('Nubank UV');
  });

  it('should propagate a deletion to every consumer reading the shared signal (regressão sessão #26)', () => {
    store.ensureLoaded();
    // dois "consumidores" leem o mesmo signal do store
    const consumerA = () => store.accounts();
    const consumerB = () => store.accounts();
    expect(consumerA().length).toBe(2);
    expect(consumerB().length).toBe(2);

    // uma tela apaga a conta; o backend passa a devolver só a que sobrou
    service.delete.and.returnValue(of(void 0));
    service.list.and.returnValue(of([carteira]));
    store.delete('1').subscribe();

    // ambos os consumidores enxergam a lista atualizada, sem reload
    expect(service.delete).toHaveBeenCalledWith('1');
    expect(consumerA()).toEqual([carteira]);
    expect(consumerB()).toEqual([carteira]);
  });

  it('should clear the signal and force a re-fetch on next ensureLoaded after reset (sessão #42 — bug de vazamento entre usuários)', () => {
    store.ensureLoaded();
    expect(store.accounts()).toEqual([nubank, carteira]);

    store.reset();

    expect(store.accounts()).toEqual([]);

    // próximo usuário logando na mesma aba: sem reset(), ensureLoaded() não faria nada
    // (loaded já era true) e essa tela continuaria mostrando os dados do usuário anterior.
    service.list.and.returnValue(of([nubank]));
    store.ensureLoaded();

    expect(service.list).toHaveBeenCalledTimes(2);
    expect(store.accounts()).toEqual([nubank]);
  });
});
