import { isPlatformServer } from '@angular/common';
import { HttpInterceptorFn } from '@angular/common/http';
import { inject, PLATFORM_ID } from '@angular/core';
import { SERVER_API_ORIGIN } from './server-api-origin.token';
import { SERVER_REQUEST_HOST } from './server-request-host.token';

/**
 * SSR only: rewrites relative /api and /uploads URLs to an absolute origin
 * (the Node render process has no browser location to resolve against),
 * and forwards the visitor's real Host as X-Forwarded-Host so hms-api's
 * DomainTenantResolutionFilter resolves the correct tenant for this
 * render - see SERVER_REQUEST_HOST for why that forwarding matters.
 */
export const ssrApiUrlInterceptor: HttpInterceptorFn = (req, next) => {
  const platformId = inject(PLATFORM_ID);
  if (!isPlatformServer(platformId)) {
    return next(req);
  }
  const origin = inject(SERVER_API_ORIGIN);
  const host = inject(SERVER_REQUEST_HOST);
  let outgoing = req;
  if (origin && outgoing.url.startsWith('/')) {
    outgoing = outgoing.clone({ url: `${origin}${outgoing.url}` });
  }
  if (host) {
    outgoing = outgoing.clone({ headers: outgoing.headers.set('X-Forwarded-Host', host) });
  }
  return next(outgoing);
};
