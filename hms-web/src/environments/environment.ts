export const environment = {
  production: true,
  apiBaseUrl: '/api',
  // Which sidenav modules this deployment shows when no license claim is
  // present (e.g. before the first login of a session) - see
  // layout/package-config.ts. Superseded by the JWT's licensedModules claim
  // once a user is signed in.
  activePackage: 'PREMIUM' as const,
  // Must match the backend's app.deployment.mode for this same deployment
  // (see hms-api's DeploymentModeProperties) - multi-tenant is the
  // deployment target this product is built for (3-field client-code
  // login). 'single-tenant' still exists as a code path for a one-off
  // offline/on-prem build, but is no longer the default.
  deploymentMode: 'multi-tenant' as 'single-tenant' | 'multi-tenant'
};
