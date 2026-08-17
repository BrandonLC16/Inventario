import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { DemoSessionService } from '../session/demo-session.service';
import { APP_SECTION_DATA_KEY, AppSection, canAccessSection } from './app-navigation';

export const demoRoleGuard: CanActivateFn = (route) => {
  const section = route.data[APP_SECTION_DATA_KEY] as AppSection | undefined;
  const session = inject(DemoSessionService);

  if (section && canAccessSection(session.role(), section)) {
    return true;
  }

  return inject(Router).createUrlTree(['/forbidden']);
};
