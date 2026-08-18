import { HttpClient, provideHttpClient } from '@angular/common/http';
import {
  EnvironmentProviders,
  inject,
  makeEnvironmentProviders,
  provideAppInitializer,
} from '@angular/core';

import { RuntimeConfigService } from '../config/runtime-config.service';
import { Configuration } from './generated/configuration';

export function provideInventoryApi(): EnvironmentProviders {
  return makeEnvironmentProviders([
    provideHttpClient(),
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
