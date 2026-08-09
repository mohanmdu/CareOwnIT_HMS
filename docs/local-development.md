# Local Development vs. Production

Short version: **nothing about local development changed or needs to be
uncommented.** Production config for the `careownitsolutions.com` VPS deploy
was layered on top via environment variables (backend) and Angular's normal
dev/prod environment file split (frontend) — neither mechanism disables or
requires editing the local defaults.

The one exception, noted explicitly below, is a single commented-out line in
`hms-website`'s production environment file, kept purely as a convenience.

## hms-api (Spring Boot)

All environment-specific config in `hms-api/src/main/resources/application.properties`
follows the pattern `${ENV_VAR:local-default}` — if the env var isn't set,
Spring falls back to the local-dev default baked into the file. Production
(the VPS) sets every one of these via a systemd `EnvironmentFile`
(`/home/CareOwn_HMS/hms-api/shared/hms-api.env`, not in git); local dev sets
none of them and just uses the defaults below.

| Env var | Local default | What it controls |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/Navjeevan?...` | Database connection |
| `DB_USER` | `root` | |
| `DB_PASSWORD` | `root` | |
| `DB_POOL_SIZE` | `20` | HikariCP pool size |
| `JWT_SECRET` | dev-only placeholder (in the file) | Token signing key |
| `JWT_EXPIRATION_MINUTES` | `600` | |
| `UPLOAD_DIR` | `uploads` (relative to wherever you launch the JVM from) | Consultant photos, CMS images, patient reports |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:*` | Allows any localhost port, since the Angular/Vite dev servers for hms-web and hms-website land on unpredictable ports |
| `DEPLOYMENT_MODE` | `multi-tenant` | 3-field client-code login, per-client license enforcement, domain-based public-site routing (see `DeploymentModeProperties`) - the deployment target this product is built for. `single-tenant` still exists as a code path but is no longer the default either locally or in production. |

**To run locally**: have a local MySQL with a `Navjeevan` database (or point
`DB_URL` elsewhere), then from `hms-api/`:

```
./mvnw spring-boot:run
```

No env vars required unless you want to override one of the defaults above.

Production only differs in that `CORS_ALLOWED_ORIGINS` is set to the real
`https://*.careownitsolutions.com` origins and `DB_URL`/`DB_USER`/`DB_PASSWORD`
point at the VPS's own `hms_demo` schema — both purely through env vars, the
source code and its defaults are identical either way.

## hms-web (Angular, admin/staff SPA)

Untouched by the deploy. Angular's CLI automatically swaps in
`src/environments/environment.development.ts` for `ng serve` and
`environment.ts` for `ng build --configuration production` — this project
already had that split before the deploy, and both files still read exactly
as they did.

`apiBaseUrl: '/api'` stays a **relative** path in both files — locally it's
resolved by `proxy.conf.json` (Angular dev-server proxy to `localhost:8080`);
in production it's resolved by nginx's own `/api` proxy on
`admin.careownitsolutions.com`. No source change was needed for either
environment.

**To run locally**: `npm start` from `hms-web/` — identical to before.

## hms-website (Angular SSR, patient-facing site)

Same dev/prod file split as hms-web. `environment.development.ts` (local) is
untouched: `apiBaseUrl: '/api'`, `hmsAppUrl: 'http://localhost:4200'`.

`environment.ts` (production only) had one value changed — `hmsAppUrl` now
points "Book Appointment" links at the real admin subdomain instead of a
relative path, since demo.\* and admin.\* are now separate subdomains rather
than the same host:

```ts
// Local path-based value, uncomment to test that routing style again:
// hmsAppUrl: '/hms'
hmsAppUrl: 'https://admin.careownitsolutions.com'
```

This is the one spot with a deliberately-commented-out alternative — it only
affects production builds (`ng build`). Running `npm start` locally never
reads this file at all, so there's nothing to uncomment for local dev either
way; the commented line is there only if you ever want a production build
that uses path-based routing again.

**To run locally**: `npm start` from `hms-website/` — identical to before.

## Node version

All three Angular apps have an `.nvmrc` pinning `20.11.1`, matching the VPS.
If you use `nvm`, run `nvm use` inside each app's directory to pick it up.
This doesn't block using a different Node version locally — it's a pin for
consistency with production, not an enforced requirement.
