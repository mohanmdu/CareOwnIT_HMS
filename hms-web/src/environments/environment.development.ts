export const environment = {
  production: false,
  apiBaseUrl: '/api',
  // Which sidenav modules this deployment shows when no license claim is
  // present (e.g. before the first login of a session) - see
  // layout/package-config.ts. Superseded by the JWT's licensedModules claim
  // once a user is signed in.
  activePackage: 'PREMIUM' as const,
  // Must match the backend's app.deployment.mode (hms-api defaults to
  // single-tenant too - see DeploymentModeProperties), so local dev's login
  // form matches whatever LoginService overload the local backend expects.
  deploymentMode: 'single-tenant' as 'single-tenant' | 'multi-tenant'
};
