import { test as base, expect } from '@playwright/test';

const API_BASE = 'http://localhost:8080/api';
const BOOTSTRAP_USERNAME = 'superadmin';
const BOOTSTRAP_PASSWORD = 'ChangeMe@123';

function decodeClaims(token: string): { mustChangePassword?: boolean } {
  const payload = token.split('.')[1];
  return JSON.parse(Buffer.from(payload, 'base64').toString('utf-8'));
}

async function login(): Promise<string> {
  const res = await fetch(`${API_BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: BOOTSTRAP_USERNAME, password: BOOTSTRAP_PASSWORD })
  });
  if (!res.ok) {
    throw new Error(`E2E setup: login as ${BOOTSTRAP_USERNAME} failed (${res.status}): ${await res.text()}`);
  }
  const { token } = (await res.json()) as { token: string };
  return token;
}

/**
 * Real auth (see com.pms.security) means every route is now gated by
 * AuthService.isAuthenticated() - these existing/generic specs never cared
 * about login, they just need SOME session so app.routes.ts's authGuard lets
 * them through. superadmin has every module permission, so it reproduces
 * exactly the "everything visible" state these specs were written against
 * before real auth existed. Specs that specifically test the login/RBAC
 * flow itself use the plain '@playwright/test' import instead of this one.
 */
async function fetchAuthToken(): Promise<string> {
  let token = await login();
  if (decodeClaims(token).mustChangePassword) {
    // Bootstrap account starts forced to change its password - clear that
    // flag once (setting the same password back is allowed) so the rest of
    // the suite gets a token that isn't restricted to just /change-password.
    const changeRes = await fetch(`${API_BASE}/auth/change-password`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ currentPassword: BOOTSTRAP_PASSWORD, newPassword: BOOTSTRAP_PASSWORD })
    });
    if (!changeRes.ok) {
      throw new Error(`E2E setup: clearing forced password change failed (${changeRes.status}): ${await changeRes.text()}`);
    }
    token = await login();
  }
  return token;
}

export const test = base.extend<{ authenticate: void }, { authToken: string }>({
  authToken: [
    async ({}, use) => {
      await use(await fetchAuthToken());
    },
    { scope: 'worker' }
  ],
  authenticate: [
    async ({ page, authToken }, use) => {
      await page.addInitScript((token) => {
        sessionStorage.setItem('hms_auth', token);
      }, authToken);
      await use();
    },
    { auto: true }
  ]
});

export { expect };
