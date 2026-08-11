import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { CardService } from './card.service';
import { Card } from './settings.models';

describe('CardService', () => {
  let service: CardService;
  let httpMock: HttpTestingController;

  const card: Card = {
    id: '1', name: 'Nubank', accountId: 'a1', accountName: 'Nubank Conta', closingDay: 28, dueDay: 7,
    archived: false
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(CardService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should list cards', () => {
    let result: Card[] = [];
    service.list().subscribe((cards) => (result = cards));

    const request = httpMock.expectOne((r) => r.url === '/api/v1/cards');
    expect(request.request.method).toBe('GET');
    request.flush([card]);

    expect(result).toEqual([card]);
  });

  it('should create a card', () => {
    service.create({ name: 'Nubank', accountId: 'a1', closingDay: 28, dueDay: 7 }).subscribe();

    const request = httpMock.expectOne('/api/v1/cards');
    expect(request.request.method).toBe('POST');
    expect(request.request.body.accountId).toBe('a1');
    request.flush(card);
  });

  it('should update a card', () => {
    service.update('1', { name: 'Nubank UV', accountId: 'a1', closingDay: 25, dueDay: 4 }).subscribe();

    const request = httpMock.expectOne('/api/v1/cards/1');
    expect(request.request.method).toBe('PUT');
    request.flush(card);
  });

  it('should delete a card', () => {
    service.delete('1').subscribe();

    const request = httpMock.expectOne('/api/v1/cards/1');
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
  });

  it('should list archived cards when requested (sessão #42)', () => {
    service.list(true).subscribe();

    const request = httpMock.expectOne((r) => r.url === '/api/v1/cards');
    expect(request.request.params.get('archived')).toBe('true');
    request.flush([{ ...card, archived: true }]);
  });

  it('should archive a card', () => {
    service.archive('1').subscribe();

    const request = httpMock.expectOne('/api/v1/cards/1/archive');
    expect(request.request.method).toBe('PATCH');
    request.flush({ ...card, archived: true });
  });

  it('should unarchive a card', () => {
    service.unarchive('1').subscribe();

    const request = httpMock.expectOne('/api/v1/cards/1/unarchive');
    expect(request.request.method).toBe('PATCH');
    request.flush(card);
  });
});
