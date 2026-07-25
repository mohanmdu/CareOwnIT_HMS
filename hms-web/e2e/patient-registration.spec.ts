import { expect, test } from './fixtures';

/**
 * Tier 2: registers a real patient via the UI (unique name/mobile per run,
 * so this works against any existing dev DB state) and confirms it lands in
 * the Active Patients list - the entry point every other clinical workflow
 * in this app depends on.
 */
test('register a new patient and see it in the Active list', async ({ page }) => {
  const unique = Date.now();
  const patientName = `Playwright Test Patient ${unique}`;
  const mobileNumber = String(unique).slice(-10);

  await page.goto('/registration/patients');
  await page.getByRole('button', { name: 'Add Patient' }).click();

  await page.getByLabel('Patient name').fill(patientName);
  await page.getByLabel('Gender').click();
  await page.getByRole('option', { name: 'Male', exact: true }).click();
  await page.getByLabel('Age').fill('30');
  await page.getByLabel('Mobile number').fill(mobileNumber);
  await page.getByLabel('Location').fill('Playwright Test Address');
  await page.getByLabel('Registered Via').click();
  await page.getByRole('option', { name: 'Front Office' }).click();

  await page.getByRole('button', { name: 'Add patient' }).click();

  await expect(page.getByRole('cell', { name: patientName, exact: true })).toBeVisible();
  await expect(page.getByRole('row', { name: new RegExp(patientName) })).toContainText(mobileNumber);
});
