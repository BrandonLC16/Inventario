import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';

import { App } from './app';
import { routes } from './app.routes';
import { SessionService } from './core/session/session.service';

const emptySession = {
  isAuthenticated: signal(false).asReadonly(),
  roles: signal([]).asReadonly(),
  user: signal(null).asReadonly(),
  login: vi.fn(),
  logout: vi.fn(() => of(undefined)),
};

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter(routes), { provide: SessionService, useValue: emptySession }],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('requires login after an unauthenticated load', async () => {
    const fixture = TestBed.createComponent(App);
    const router = TestBed.inject(Router);

    await router.navigateByUrl('/');
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(router.url).toContain('/login');
    expect(compiled.querySelector('h1')?.textContent).toContain('Inicia sesión');
  });
});
