import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { CategoryStore } from './category.store';
import { CategoryService } from '../../features/settings/category.service';
import { Category } from '../../features/settings/settings.models';

describe('CategoryStore', () => {
  let store: CategoryStore;
  let service: jasmine.SpyObj<CategoryService>;

  const mercado: Category = { id: '1', name: 'Mercado', icon: '🛒', color: '#3fb950', kind: 'EXPENSE' };

  beforeEach(() => {
    service = jasmine.createSpyObj<CategoryService>('CategoryService', ['list', 'create', 'update', 'delete']);
    service.list.and.returnValue(of([mercado]));

    TestBed.configureTestingModule({
      providers: [{ provide: CategoryService, useValue: service }]
    });
    store = TestBed.inject(CategoryStore);
  });

  it('should load once and expose the shared signal', () => {
    store.ensureLoaded();
    store.ensureLoaded();

    expect(service.list).toHaveBeenCalledTimes(1);
    expect(store.categories()).toEqual([mercado]);
  });

  it('should refresh after create', () => {
    store.ensureLoaded();
    service.create.and.returnValue(of(mercado));
    store.create({ name: 'Mercado', icon: null, color: null, kind: 'EXPENSE' }).subscribe();
    expect(service.create).toHaveBeenCalled();
    expect(service.list).toHaveBeenCalledTimes(2);
  });

  it('should refresh after update', () => {
    store.ensureLoaded();
    service.update.and.returnValue(of(mercado));
    store.update('1', { name: 'Super', icon: null, color: null, kind: 'EXPENSE' }).subscribe();
    expect(service.update).toHaveBeenCalledWith('1', jasmine.objectContaining({ name: 'Super' }));
    expect(service.list).toHaveBeenCalledTimes(2);
  });

  it('should refresh after delete', () => {
    store.ensureLoaded();
    service.delete.and.returnValue(of(void 0));
    service.list.and.returnValue(of([]));
    store.delete('1').subscribe();
    expect(store.categories()).toEqual([]);
  });

  it('should clear the signal and force a re-fetch on next ensureLoaded after reset (sessão #42)', () => {
    store.ensureLoaded();
    expect(store.categories()).toEqual([mercado]);

    store.reset();

    expect(store.categories()).toEqual([]);
    store.ensureLoaded();
    expect(service.list).toHaveBeenCalledTimes(2);
  });
});
