import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { CardStore } from './card.store';
import { CardService } from '../../features/settings/card.service';
import { Card } from '../../features/settings/settings.models';

describe('CardStore', () => {
  let store: CardStore;
  let service: jasmine.SpyObj<CardService>;

  const nubank: Card = {
    id: '1', name: 'Nubank', accountId: 'a1', accountName: 'Conta', closingDay: 28, dueDay: 7, archived: false
  };

  beforeEach(() => {
    service = jasmine.createSpyObj<CardService>('CardService', ['list', 'create', 'update', 'delete']);
    service.list.and.returnValue(of([nubank]));

    TestBed.configureTestingModule({
      providers: [{ provide: CardService, useValue: service }]
    });
    store = TestBed.inject(CardStore);
  });

  it('should load once and expose the shared signal', () => {
    store.ensureLoaded();
    store.ensureLoaded();

    expect(service.list).toHaveBeenCalledTimes(1);
    expect(store.cards()).toEqual([nubank]);
  });

  it('should refresh after create', () => {
    store.ensureLoaded();
    service.create.and.returnValue(of(nubank));
    store.create({ name: 'Nubank', accountId: 'a1', closingDay: 28, dueDay: 7 }).subscribe();
    expect(service.create).toHaveBeenCalled();
    expect(service.list).toHaveBeenCalledTimes(2);
  });

  it('should refresh after update', () => {
    store.ensureLoaded();
    service.update.and.returnValue(of(nubank));
    store.update('1', { name: 'Nubank UV', accountId: 'a1', closingDay: 25, dueDay: 4 }).subscribe();
    expect(service.update).toHaveBeenCalledWith('1', jasmine.objectContaining({ name: 'Nubank UV' }));
    expect(service.list).toHaveBeenCalledTimes(2);
  });

  it('should refresh after delete', () => {
    store.ensureLoaded();
    service.delete.and.returnValue(of(void 0));
    service.list.and.returnValue(of([]));
    store.delete('1').subscribe();
    expect(store.cards()).toEqual([]);
  });

  it('should clear the signal and force a re-fetch on next ensureLoaded after reset (sessão #42)', () => {
    store.ensureLoaded();
    expect(store.cards()).toEqual([nubank]);

    store.reset();

    expect(store.cards()).toEqual([]);
    store.ensureLoaded();
    expect(service.list).toHaveBeenCalledTimes(2);
  });
});
