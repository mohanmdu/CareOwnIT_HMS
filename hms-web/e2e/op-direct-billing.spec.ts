import { expect, test } from './fixtures';

/**
 * Tier 2: end-to-end walk-in OP Direct Billing charge. Creates its own
 * prerequisites (an OP Billing Category + Component, and a fresh patient)
 * via the UI so this passes against any existing dev DB state, then bills a
 * charge and confirms the receipt dialog opens.
 */
test('bill a walk-in OP Direct Billing charge end to end', async ({ page }) => {
  const unique = Date.now();
  const categoryName = `PW Category ${unique}`;
  const componentName = `PW Component ${unique}`;
  const patientName = `PW Billing Patient ${unique}`;
  const mobileNumber = String(unique).slice(-10);

  await test.step('create an OP Billing Category', async () => {
    await page.goto('/masters/op-billing-categories');
    await page.getByLabel(/category name/i).fill(categoryName);
    await page.getByRole('button', { name: 'Add category' }).click();
    // Paginated list - search for the new row rather than assume page 1.
    await page.getByPlaceholder('Search categorys...').fill(categoryName);
    await expect(page.getByRole('cell', { name: categoryName })).toBeVisible();
  });

  await test.step('create an OP Billing Component under that category', async () => {
    await page.goto('/masters/op-billing-components');
    await page.getByRole('button', { name: 'Add Component' }).click();
    await page.getByLabel('Category').click();
    await page.getByRole('option', { name: categoryName }).click();
    await page.getByLabel('Component Name').fill(componentName);
    await page.getByLabel('Amount').fill('150');
    await page.getByRole('button', { name: 'Add', exact: true }).click();
    await expect(page.getByRole('cell', { name: componentName, exact: true })).toBeVisible();
  });

  await test.step('register a patient to bill', async () => {
    await page.goto('/registration/patients');
    await page.getByRole('button', { name: 'Add Patient' }).click();
    await page.getByLabel('Patient name').fill(patientName);
    await page.getByLabel('Gender').click();
    await page.getByRole('option', { name: 'Female', exact: true }).click();
    await page.getByLabel('Age').fill('40');
    await page.getByLabel('Mobile number').fill(mobileNumber);
    await page.getByLabel('Location').fill('Playwright Test Address');
    await page.getByLabel('Registered Via').click();
    await page.getByRole('option', { name: 'Front Office' }).click();
    await page.getByRole('button', { name: 'Add patient' }).click();
    await expect(page.getByRole('cell', { name: patientName, exact: true })).toBeVisible();
  });

  await test.step('bill the patient via OP Direct Billing', async () => {
    await page.goto('/appointments/direct-billing');
    await page.getByLabel(/search by patient id, name or mobile number/i).fill(mobileNumber);
    await page.getByRole('button', { name: 'Search' }).click();
    await page.getByRole('button', { name: 'Select' }).click();

    await page.getByLabel('Category').click();
    await page.getByRole('option', { name: categoryName }).click();
    await page.getByLabel('Component').click();
    await page.getByRole('option', { name: componentName }).click();

    // Single-item direct-submit form, no separate "Add to list" step -
    // Quantity defaults to 1 and Amount auto-fills from the Component's
    // price (see OpDirectBillingComponent.onComponentChange()).
    await page.getByRole('button', { name: 'Submit' }).click();

    await expect(page.getByRole('heading', { name: 'OP Direct Billing Receipt' })).toBeVisible();
  });
});
