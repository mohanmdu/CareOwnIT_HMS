import { APP_BASE_HREF } from '@angular/common';
import { CommonEngine } from '@angular/ssr';
import express from 'express';
import { createProxyMiddleware } from 'http-proxy-middleware';
import { fileURLToPath } from 'node:url';
import { dirname, join, resolve } from 'node:path';
import bootstrap from './src/main.server';
import { requestHostStorage } from './src/app/core/request-host.store';

/**
 * The domain the browser actually asked for, not this Node process's own
 * address - same precedence/parsing as hms-api's DomainTenantResolutionFilter
 * (X-Forwarded-Host first, since production terminates TLS at nginx in
 * front of this process), so the two sides agree on which domain a given
 * request is "for".
 */
function resolveRequestHost(req: express.Request): string | null {
  const forwardedHeader = req.headers['x-forwarded-host'];
  const forwarded = Array.isArray(forwardedHeader) ? forwardedHeader[0] : forwardedHeader;
  const raw = forwarded ?? req.headers.host;
  if (!raw) {
    return null;
  }
  return raw.split(',')[0].trim().split(':')[0].toLowerCase();
}

// The Express app is exported so that it can be used by serverless Functions.
export function app(): express.Express {
  const server = express();
  const serverDistFolder = dirname(fileURLToPath(import.meta.url));
  const browserDistFolder = resolve(serverDistFolder, '../browser');
  const indexHtml = join(serverDistFolder, 'index.server.html');

  const commonEngine = new CommonEngine();

  server.set('view engine', 'html');
  server.set('views', browserDistFolder);

  // Browser-side (post-hydration) calls to /api and /uploads have no reverse
  // proxy in front of this standalone Node process, so they'd otherwise fall
  // through to the Angular catch-all below and get back HTML instead of JSON.
  // A real deployment may put its own reverse proxy in front of this server
  // instead - this just makes the server work standalone either way.
  const apiOrigin = process.env['API_ORIGIN'] ?? 'http://localhost:8080';
  // xfwd: true adds X-Forwarded-Host (from this request's own Host header)
  // when the browser call didn't already arrive with one - same tenant
  // resolution concern as SERVER_REQUEST_HOST below, for the browser-side
  // (post-hydration) leg instead of the SSR-render leg.
  server.use(createProxyMiddleware({ target: apiOrigin, changeOrigin: true, xfwd: true, pathFilter: ['/api', '/uploads'] }));

  // Serve static files from /browser
  server.get('**', express.static(browserDistFolder, {
    maxAge: '1y',
    index: 'index.html',
  }));

  // All regular routes use the Angular engine
  server.get('**', (req, res, next) => {
    const { protocol, originalUrl, baseUrl, headers } = req;

    // See request-host.store.ts: the render's own outbound API calls read
    // the host back out of this store (via SERVER_REQUEST_HOST's factory in
    // app.config.server.ts), not from commonEngine.render()'s `providers`
    // option - that becomes a *platform* provider, which a `providedIn:
    // 'root'` token's own factory always wins over from app code.
    requestHostStorage.run(resolveRequestHost(req), () => {
      commonEngine
        .render({
          bootstrap,
          documentFilePath: indexHtml,
          url: `${protocol}://${headers.host}${originalUrl}`,
          publicPath: browserDistFolder,
          providers: [{ provide: APP_BASE_HREF, useValue: baseUrl }],
        })
        .then((html) => res.send(html))
        .catch((err) => next(err));
    });
  });

  return server;
}

function run(): void {
  const port = process.env['PORT'] || 4000;

  // Start up the Node server
  const server = app();
  server.listen(port, () => {
    console.log(`Node Express server listening on http://localhost:${port}`);
  });
}

run();
