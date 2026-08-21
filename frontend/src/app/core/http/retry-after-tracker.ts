import { Injectable, computed, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { map, timer } from 'rxjs';

@Injectable()
export class RetryAfterTracker {
  private readonly deadline = signal(0);
  private readonly now = toSignal(timer(0, 1000).pipe(map(() => Date.now())), {
    initialValue: Date.now(),
  });

  readonly remainingSeconds = computed(() =>
    Math.max(0, Math.ceil((this.deadline() - this.now()) / 1000)),
  );
  readonly blocked = computed(() => this.remainingSeconds() > 0);

  block(seconds: number | undefined): void {
    const safeSeconds =
      seconds !== undefined && Number.isFinite(seconds) ? Math.max(0, Math.ceil(seconds)) : 0;
    this.deadline.set(safeSeconds > 0 ? Date.now() + safeSeconds * 1000 : 0);
  }
}
