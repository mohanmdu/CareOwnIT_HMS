# HMS Modernization

This folder contains the target-state modernization of the legacy Struts2/Hibernate3
Hospital Management System at `d:/project/Navjeevan/Navjeenna`.

- [`docs/local-development.md`](docs/local-development.md) - how local dev config relates to
  the `careownitsolutions.com` production deploy (read this if you're setting up locally).
- [`hms-web/`](hms-web) - Angular 18 SPA, admin/staff-facing HMS UI (standalone components, lazy-loaded feature routes).
- [`hms-api/`](hms-api) - Spring Boot 4 REST API (Java 17, Spring Data JPA, Flyway, Spring Security).
- [`hms-website/`](hms-website) - Angular 18 SSR app, the patient-facing public hospital site.

Both projects currently contain one complete, working vertical slice - **Departments**
(`hms-api`: `com.pms.masters.*`, `hms-web`: `features/masters-admin/departments`) - as
the reference shape for porting the remaining modules called out in the migration plan.

## Running locally

**Backend** (needs JDK 17+; `JAVA_HOME` should point at a 17+ install, e.g. the
`jdk-23.0.1` already present on this machine, not the older `jdk-11`/`jdk1.8` also
installed):

```
cd hms-api
./mvnw spring-boot:run
```

Configure the datasource via environment variables (`DB_URL`, `DB_USER`, `DB_PASSWORD`)
or edit `src/main/resources/application.properties` directly. Do not point this at the
live legacy `Navjeevan` database - `ddl-auto` is set to `validate`, not `update`, and
Flyway's `V1__baseline.sql` is a placeholder that was never reconciled against a real
export of that live schema. See [`docs/local-development.md`](docs/local-development.md)
for the full list of env vars and their local-dev defaults.

**Frontend**:

```
cd hms-web
npm start
```

Serves on `http://localhost:4200`, proxying API calls to `http://localhost:8080/api`
(see `src/environments/environment.development.ts`). Auth is real DB-backed JWT login
(see `com.pms.security`) - use whatever account you've created via the backend, or the
seeded bootstrap account (`superadmin`, password rotated on every real deployment,
`must_change_password` enforced).
