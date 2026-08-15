import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Faq } from './faq';

describe('Faq', () => {
  let fixture: ComponentFixture<Faq>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Faq],
      providers: [provideRouter([])]
    }).compileComponents();

    fixture = TestBed.createComponent(Faq);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render every question', () => {
    const text = fixture.nativeElement.textContent;
    for (const item of fixture.componentInstance.items) {
      expect(text).toContain(item.question);
    }
  });

  it('should link back to the landing and to login', () => {
    expect(fixture.nativeElement.querySelector('a[href="/"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('a[href="/login"]')).toBeTruthy();
  });
});
