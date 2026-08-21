import { TestBed } from '@angular/core/testing';

import { RetryAfterTracker } from './retry-after-tracker';

describe('RetryAfterTracker', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('blocks for the requested duration and re-enables without an automatic action', () => {
    const tracker = TestBed.runInInjectionContext(() => new RetryAfterTracker());

    tracker.block(2);
    expect(tracker.blocked()).toBe(true);
    expect(tracker.remainingSeconds()).toBe(2);

    vi.advanceTimersByTime(1000);
    expect(tracker.remainingSeconds()).toBe(1);

    vi.advanceTimersByTime(1000);
    expect(tracker.blocked()).toBe(false);
    expect(tracker.remainingSeconds()).toBe(0);
  });
});
