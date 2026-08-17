import { TestBed } from '@angular/core/testing';

import { DemoSessionService } from './demo-session.service';

describe('DemoSessionService', () => {
  it('keeps a replaceable demo identity in memory', () => {
    const session = TestBed.inject(DemoSessionService);

    expect(session.role()).toBe('ADMIN');

    session.setRole('SALES');

    expect(session.identity()).toEqual({ displayName: 'Sofía Ventas', role: 'SALES' });
    expect(session.roleLabel()).toBe('Ventas');
  });
});
