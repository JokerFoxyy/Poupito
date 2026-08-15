import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { RecurringService } from './recurring.service';
import { RecurringPayload } from './recurring.models';

describe('RecurringService', () => {
  let service: RecurringService;
  let httpMock: HttpTestingController;

  const payload: RecurringPayload = {
    description: 'Spotify', amount: 27.9, type: 'EXPENSE',
    accountId: 'a1', categoryId: 'c1', dayOfMonth: 10, active: true, endDate: null
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(RecurringService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should list recurring transactions', () => {
    service.list().subscribe();
    const req = httpMock.expectOne((r) => r.url === '/api/v1/recurring');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('should create a recurring transaction', () => {
    service.create(payload).subscribe();
    const req = httpMock.expectOne('/api/v1/recurring');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.description).toBe('Spotify');
    req.flush({});
  });

  it('should update and delete', () => {
    service.update('r1', payload).subscribe();
    const put = httpMock.expectOne('/api/v1/recurring/r1');
    expect(put.request.method).toBe('PUT');
    put.flush({});

    service.delete('r1').subscribe();
    const del = httpMock.expectOne('/api/v1/recurring/r1');
    expect(del.request.method).toBe('DELETE');
    del.flush(null);
  });

  it('should fetch occurrences with month param', () => {
    service.occurrences('2026-07').subscribe();
    const req = httpMock.expectOne((r) => r.url === '/api/v1/recurring/occurrences');
    expect(req.request.params.get('month')).toBe('2026-07');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('should materialize with month param', () => {
    service.materialize('2026-07').subscribe();
    const req = httpMock.expectOne((r) => r.url === '/api/v1/recurring/materialize');
    expect(req.request.method).toBe('POST');
    expect(req.request.params.get('month')).toBe('2026-07');
    req.flush([]);
  });

  it('should set paid on a transaction', () => {
    service.setPaid('t1', true).subscribe();
    const req = httpMock.expectOne('/api/v1/transactions/t1/paid');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ paid: true });
    req.flush({});
  });

  it('should list archived recurrings when requested (sessão #42)', () => {
    service.list(true).subscribe();
    const req = httpMock.expectOne((r) => r.url === '/api/v1/recurring');
    expect(req.request.params.get('archived')).toBe('true');
    req.flush([]);
  });

  it('should archive a recurring', () => {
    service.archive('r1').subscribe();
    const req = httpMock.expectOne('/api/v1/recurring/r1/archive');
    expect(req.request.method).toBe('PATCH');
    req.flush({});
  });

  it('should unarchive a recurring', () => {
    service.unarchive('r1').subscribe();
    const req = httpMock.expectOne('/api/v1/recurring/r1/unarchive');
    expect(req.request.method).toBe('PATCH');
    req.flush({});
  });
});
