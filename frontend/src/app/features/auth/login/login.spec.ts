import { HttpErrorResponse } from '@angular/common/http';
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
    expect(component['errorMessage']()).toContain('campos obligatorios');

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
            error: { message: 'internal detail must not be rendered' },
          }),
      ),
    );
    const component = fixture.componentInstance;
    component['form'].setValue({ identifier: 'alicia', password: 'secret' });

    component['submit']();
    fixture.detectChanges();

    expect(component['form'].controls.password.value).toBe('');
    expect(fixture.nativeElement.textContent).not.toContain('internal detail');
    expect(fixture.nativeElement.textContent).toContain('No fue posible iniciar sesión');
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
