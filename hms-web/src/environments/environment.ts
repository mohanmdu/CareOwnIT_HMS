export const environment = {
  production: true,
  apiBaseUrl: '/api',
  // Which sidenav modules this deployment shows when no license claim is
  // present (e.g. before the first login of a session) - see
  // layout/package-config.ts. Superseded by the JWT's licensedModules claim
  // once a user is signed in.
  activePackage: 'PREMIUM' as const,
  // Must match the backend's app.deployment.mode for this same deployment
  // (see hms-api's DeploymentModeProperties) - 'single-tenant' (the default
  // for every offline/on-prem build) hides the Client Code login field;
  // only the shared multi-tenant VPS build sets this to 'multi-tenant'.
  deploymentMode: 'single-tenant' as 'single-tenant' | 'multi-tenant'
};
