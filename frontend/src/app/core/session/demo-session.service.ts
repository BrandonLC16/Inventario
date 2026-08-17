import { computed, Injectable, signal } from '@angular/core';

import { AppRole, ROLE_LABELS } from '../navigation/app-navigation';

interface DemoIdentity {
  readonly displayName: string;
  readonly role: AppRole;
}

const DEMO_IDENTITIES: Readonly<Record<AppRole, DemoIdentity>> = {
  ADMIN: { displayName: 'Alicia Admin', role: 'ADMIN' },
  INVENTORY_MANAGER: { displayName: 'Mario Inventario', role: 'INVENTORY_MANAGER' },
  SALES: { displayName: 'Sofía Ventas', role: 'SALES' },
};

@Injectable({ providedIn: 'root' })
export class DemoSessionService {
  private readonly identityState = signal<DemoIdentity>(DEMO_IDENTITIES.ADMIN);

  readonly identity = this.identityState.asReadonly();
  readonly role = computed(() => this.identityState().role);
  readonly roleLabel = computed(() => ROLE_LABELS[this.role()]);

  setRole(role: AppRole): void {
    this.identityState.set(DEMO_IDENTITIES[role]);
  }
}
