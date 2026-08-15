import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Landing } from './landing';

describe('Landing', () => {
  let fixture: ComponentFixture<Landing>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Landing],
      providers: [provideRouter([])]
    }).compileComponents();

    fixture = TestBed.createComponent(Landing);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the hero and a CTA to create an account', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Poupito');
    expect(text).toContain('Criar conta grátis');
  });

  it('should list all the features', () => {
    const text = fixture.nativeElement.textContent;
    for (const feature of fixture.componentInstance.features) {
      expect(text).toContain(feature.title);
    }
  });

  it('should link to the FAQ', () => {
    const link = fixture.nativeElement.querySelector('a[href="/faq"]');
    expect(link).toBeTruthy();
  });
});
