import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { convertToParamMap, ActivatedRoute, provideRouter, Router } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';

import { SessionService } from '../../../core/session/session.service';
import { Login } from './login';

describe('Login', () => {
  let fixture: ComponentFixture<Login>;
  let loginResult: Subject<unknown>;
  let session: { login: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    loginResult = new Subject();
    session = { login: vi.fn(() => loginResult) };

    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap({ returnUrl: '/orders' }) } },
        },
        { provide: SessionService, useValue: session },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Login);
    fixture.detectChanges();
  });

  it('validates required fields and prevents duplicate submission', () => {
    const component = fixture.componentInstance;
    component['submit']();
    expect(session.login).not.toHaveBeenCalled();
    expect(component['localErrorMessage']()).toContain('campos obligatorios');

    component['form'].setValue({ identifier: 'alicia', password: 'secret' });
    component['submit']();
    component['submit']();

    expect(session.login).toHaveBeenCalledOnce();
  });

  it('navigates only to an internal return URL after login', async () => {
    const component = fixture.componentInstance;
    const router = TestBed.inject(Router);
    const navigation = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    component['form'].setValue({ identifier: 'alicia', password: 'secret' });

    component['submit']();
    loginResult.next({});
    loginResult.complete();
    await Promise.resolve();

    expect(navigation).toHaveBeenCalledWith('/orders');
  });

  it('clears the password and never exposes the server error after failure', () => {
    session.login.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 401,
            error: {
              code: 'AUTHENTICATION_FAILED',
              message: 'internal detail with refreshToken=secret must not be rendered',
              correlationId: 'login-corr-01',
            },
          }),
      ),
    );
    const component = fixture.componentInstance;
    component['form'].setValue({ identifier: 'alicia', password: 'secret' });

    component['submit']();
    fixture.detectChanges();

    expect(component['form'].controls.password.value).toBe('');
    expect(fixture.nativeElement.textContent).not.toContain('internal detail');
    expect(fixture.nativeElement.textContent).not.toContain('refreshToken=secret');
    expect(fixture.nativeElement.textContent).toContain('No fue posible iniciar sesión');
    expect(
      (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('input[readonly]')
        ?.value,
    ).toBe('login-corr-01');
  });

  it('associates API validation errors with their controls and announces a summary', () => {
    session.login.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: {
              code: 'VALIDATION_FAILED',
              validationErrors: { identifier: 'El identificador no es válido.' },
            },
          }),
      ),
    );
    const component = fixture.componentInstance;
    component['form'].setValue({ identifier: 'alicia', password: 'secret' });

    component['submit']();
    fixture.detectChanges();

    expect(component['form'].controls.identifier.getError('api')).toBe(
      'El identificador no es válido.',
    );
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Hay campos que requieren atención',
    );
    expect(element.querySelector('[aria-label="Resumen de campos con error"]')).not.toBeNull();
  });

  it('respects Retry-After by temporarily disabling login without resubmitting', () => {
    session.login.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 429,
            headers: new HttpHeaders({ 'Retry-After': '5' }),
            error: { code: 'RATE_LIMIT_EXCEEDED' },
          }),
      ),
    );
    const component = fixture.componentInstance;
    component['form'].setValue({ identifier: 'alicia', password: 'secret' });

    component['submit']();
    fixture.detectChanges();
    component['submit']();

    const button = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>(
      'form button[type="submit"]',
    );
    expect(component['retryAfter'].blocked()).toBe(true);
    expect(button?.disabled).toBe(true);
    expect(button?.textContent).toContain('Disponible en');
    expect(session.login).toHaveBeenCalledOnce();
  });

  it('falls back to the dashboard for an external-looking return URL', async () => {
    TestBed.resetTestingModule();
    const localSession = { login: vi.fn(() => of({})) };
    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { queryParamMap: convertToParamMap({ returnUrl: '//evil.test' }) },
          },
        },
        { provide: SessionService, useValue: localSession },
      ],
    }).compileComponents();
    const localFixture = TestBed.createComponent(Login);
    const component = localFixture.componentInstance;
    const router = TestBed.inject(Router);
    const navigation = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    component['form'].setValue({ identifier: 'alicia', password: 'secret' });

    component['submit']();

    expect(navigation).toHaveBeenCalledWith('/dashboard');
  });
});
