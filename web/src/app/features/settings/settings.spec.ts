import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { Settings } from './settings';

describe('Settings', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Settings],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    }).compileComponents();
  });

  it('should render the page title and both panels', () => {
    const fixture = TestBed.createComponent(Settings);
    fixture.detectChanges();

    const httpMock = TestBed.inject(HttpTestingController);
    // accounts-panel e cards-panel carregam contas; cards-panel carrega cartões (ativos e
    // arquivados, sessão #42); categories-panel carrega categorias
    httpMock.match('/api/v1/accounts').forEach((req) => req.flush([]));
    httpMock.match((r) => r.url === '/api/v1/cards').forEach((req) => req.flush([]));
    httpMock.expectOne('/api/v1/categories').flush([]);

    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('h1')?.textContent).toContain('Configurações');
    expect(element.querySelector('app-accounts-panel')).not.toBeNull();
    expect(element.querySelector('app-cards-panel')).not.toBeNull();
    expect(element.querySelector('app-categories-panel')).not.toBeNull();
    httpMock.verify();
  });
});
