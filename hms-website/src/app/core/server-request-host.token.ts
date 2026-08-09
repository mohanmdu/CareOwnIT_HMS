import { InjectionToken } from '@angular/core';

/**
 * The domain the browser actually requested, as seen by nginx/this Node
 * process - injected per-render in server.ts, consumed by
 * ssr-api-url.interceptor.ts so the SSR data-fetch call to hms-api carries
 * it forward (as X-Forwarded-Host). Without this, hms-api's
 * DomainTenantResolutionFilter only ever sees this Node process's own
 * outbound request (Host: localhost:8080), never the visitor's real
 * domain, so the very first render of a multi-tenant public site would
 * resolve the wrong tenant even though everything after hydration
 * (browser-side calls, which do carry the real Host) would be correct.
 *
 * Browser-side, this token is never provided (stays null) - the browser's
 * own requests already carry their real Host header with no help needed.
 */
export const SERVER_REQUEST_HOST = new InjectionToken<string | null>('SERVER_REQUEST_HOST', {
  providedIn: 'root',
  factory: () => null
});
