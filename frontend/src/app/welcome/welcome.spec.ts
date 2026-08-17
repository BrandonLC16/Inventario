import { TestBed } from '@angular/core/testing';

import { Welcome } from './welcome';

describe('Welcome', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Welcome],
    }).compileComponents();
  });

  it('should expose the foundation status with a single main heading', () => {
    const fixture = TestBed.createComponent(Welcome);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('main')).not.toBeNull();
    expect(element.querySelectorAll('h1')).toHaveLength(1);
    expect(element.querySelector('h1')?.textContent).toBe('Inventario');
  });
});
