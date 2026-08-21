import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { SessionService } from '../session/session.service';
import { APP_SECTION_DATA_KEY, AppSection, canAccessSection } from './app-navigation';

export const authenticatedGuard: CanActivateFn = (_route, state) => {
  const session = inject(SessionService);

  if (session.isAuthenticated()) {
    return true;
  }

  return inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

export const roleGuard: CanActivateFn = (route) => {
  const section = route.data[APP_SECTION_DATA_KEY] as AppSection | undefined;
  const session = inject(SessionService);

  if (!section || canAccessSection(session.roles(), section)) {
    return true;
  }

  return inject(Router).createUrlTree(['/forbidden']);
};
