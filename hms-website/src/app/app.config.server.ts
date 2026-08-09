import { ApplicationConfig, mergeApplicationConfig } from '@angular/core';
import { provideServerRendering } from '@angular/platform-server';
import { appConfig } from './app.config';
import { SERVER_API_ORIGIN } from './core/server-api-origin.token';
import { SERVER_REQUEST_HOST } from './core/server-request-host.token';
import { requestHostStorage } from './core/request-host.store';

const serverConfig: ApplicationConfig = {
  providers: [
    provideServerRendering(),
    { provide: SERVER_API_ORIGIN, useValue: process.env['API_ORIGIN'] ?? 'http://localhost:8080' },
    // A factory (not useValue) because this must read the *current* render's
    // value, not whatever was current when the server process started - see
    // request-host.store.ts for why this can't just be one of
    // commonEngine.render()'s own per-render `providers` instead.
    { provide: SERVER_REQUEST_HOST, useFactory: () => requestHostStorage.getStore() ?? null }
  ]
};

export const config = mergeApplicationConfig(appConfig, serverConfig);
