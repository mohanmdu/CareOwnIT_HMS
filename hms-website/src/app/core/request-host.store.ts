import { AsyncLocalStorage } from 'node:async_hooks';

/**
 * Server-only. Threads the current request's resolved Host across the
 * async chain from server.ts's Express handler through to the SSR render's
 * own HttpClient calls, without relying on Angular DI for the per-request
 * part - commonEngine.render()'s own `providers` option becomes *platform*
 * providers (a parent of the app's root injector), and a `providedIn: 'root'`
 * token's own factory always wins over an ancestor platform provider for
 * the same token, so passing a per-request value that way is silently never
 * seen by app code (confirmed by hand: the interceptor's inject() call kept
 * returning null despite the correct host being resolved and passed in).
 * AsyncLocalStorage survives the async/await chain regardless of DI
 * boundaries, which is what's actually needed here.
 *
 * This file must never be imported from anywhere reachable by the browser
 * bundle (app.config.ts, the interceptor itself, etc.) - only from
 * server.ts and app.config.server.ts, both server-only. node:async_hooks
 * has no browser equivalent.
 */
export const requestHostStorage = new AsyncLocalStorage<string | null>();
