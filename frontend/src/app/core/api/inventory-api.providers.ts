import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  EnvironmentProviders,
  inject,
  makeEnvironmentProviders,
  provideAppInitializer,
} from '@angular/core';

import { RuntimeConfigService } from '../config/runtime-config.service';
import { sessionInterceptor } from '../session/session.interceptor';
import { Configuration } from './generated/configuration';

export function provideInventoryApi(): EnvironmentProviders {
  return makeEnvironmentProviders([
    provideHttpClient(withInterceptors([sessionInterceptor])),
    provideAppInitializer(() => inject(RuntimeConfigService).load(inject(HttpClient))),
    {
      provide: Configuration,
      useFactory: (runtimeConfig: RuntimeConfigService) =>
        new Configuration({
          basePath: runtimeConfig.apiBaseUrl,
          withCredentials: false,
        }),
      deps: [RuntimeConfigService],
    },
  ]);
}
