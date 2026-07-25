import { expect, test } from './fixtures';

/**
 * Tier 2 flagship test: exercises the entire GRN/stock-ledger/billing/return
 * chain built this session, end to end, as one continuous storyline (each
 * stage a test.step so a failure clearly shows which stage broke). Creates
 * every prerequisite (Supplier/Manufacturer/Product Type/Rack/Product) via
 * the UI so this passes against any existing dev DB state.
 *
 * Dialog content is always scoped via page.getByRole('dialog') - Angular
 * Material tab-groups keep inactive tab bodies in the DOM (aria-hidden, not
 * removed), and some of their accessible names (e.g. a "Product Type
 * Master" tab panel) substring-match unrelated field labels (e.g. "Product
 * Type") when queried from the top-level `page`, so any locator meant for a
 * dialog's contents must be scoped to the dialog, not just found on `page`.
 */
test('Pharmacy: PO -> GRN -> stock credit -> billing -> sales return -> approval -> report', async ({ page }) => {
  const unique = Date.now();
  const supplierName = `PW Supplier ${unique}`;
  const manufacturerName = `PW Manufacturer ${unique}`;
  const productTypeName = `PW Type ${unique}`;
  const rackName = `PW Rack ${unique}`;
  const productName = `PW Product ${unique}`;
  const patientName = `PW Pharmacy Patient ${unique}`;
  const mobileNumber = String(unique).slice(-10);
  const batch = `PWB${unique}`.slice(0, 20);

  let billNumber = '';

  // The app uses provideNativeDateAdapter() with no MAT_DATE_LOCALE override,
  // so datepicker inputs parse via the browser's default Date.parse - MM/DD/YYYY.
  const today = new Date();
  const todayStr = `${String(today.getMonth() + 1).padStart(2, '0')}/${String(today.getDate()).padStart(2, '0')}/${today.getFullYear()}`;

  await test.step('create Supplier, Manufacturer, Product Type, Rack masters', async () => {
    await page.goto('/pharmacy/inventory-master');

    // Supplier Master (default tab)
    await page.getByRole('button', { name: 'Add Supplier' }).click();
    let dialog = page.getByRole('dialog');
    await dialog.getByLabel('Supplier Name').fill(supplierName);
    await dialog.getByLabel('Mobile Number').fill(mobileNumber);
    await dialog.getByLabel('Address').fill('Playwright Test Address');
    await dialog.getByRole('button', { name: 'Add', exact: true }).click();
    await expect(page.getByRole('cell', { name: supplierName, exact: true })).toBeVisible();

    // Manufacturer Master
    await page.getByRole('tab', { name: 'Manufacturer Master' }).click();
    await page.getByRole('button', { name: 'Add Manufacturer' }).click();
    dialog = page.getByRole('dialog');
    await dialog.getByLabel('Manufacturer Name').fill(manufacturerName);
    await dialog.getByLabel('Address').fill('Playwright Test Address');
    await dialog.getByLabel('City').fill('Chennai');
    await dialog.getByLabel('State').fill('Tamil Nadu');
    await dialog.getByRole('button', { name: 'Add', exact: true }).click();
    await expect(page.getByRole('cell', { name: manufacturerName, exact: true })).toBeVisible();

    // Product Type Master (generic master-crud: name field + "Add product type").
    // Paginated list, so search for the new row rather than assume page 1.
    await page.getByRole('tab', { name: 'Product Type Master' }).click();
    await page.getByLabel(/new product type name/i).fill(productTypeName);
    await page.getByRole('button', { name: 'Add product type' }).click();
    await page.getByPlaceholder('Search product types...').fill(productTypeName);
    await expect(page.getByRole('cell', { name: productTypeName, exact: true })).toBeVisible();

    // Rack Master (generic master-crud: name field + "Add rack") - same pagination caveat.
    await page.getByRole('tab', { name: 'Rack Master' }).click();
    await page.getByLabel(/new rack name/i).fill(rackName);
    await page.getByRole('button', { name: 'Add rack' }).click();
    await page.getByPlaceholder('Search racks...').fill(rackName);
    await expect(page.getByRole('cell', { name: rackName, exact: true })).toBeVisible();

    // Product Master
    await page.getByRole('tab', { name: 'Product Master' }).click();
    await page.getByRole('button', { name: 'Add Product' }).click();
    dialog = page.getByRole('dialog');
    await dialog.getByLabel('Product Name').fill(productName);
    await dialog.getByLabel('Product Type').click();
    await page.getByRole('option', { name: productTypeName, exact: true }).click();
    await dialog.getByLabel('Rack Number').click();
    await page.getByRole('option', { name: rackName, exact: true }).click();
    await dialog.getByLabel('Manufacturer Name').click({ force: true });
    await page.getByRole('option', { name: manufacturerName, exact: true }).click();
    await dialog.getByRole('button', { name: 'Create' }).click();
    await expect(page.getByRole('cell', { name: productName, exact: true })).toBeVisible();
  });

  await test.step('raise a Purchase Order for the new product', async () => {
    await page.goto('/pharmacy/purchase-management');
    // "Raise & Approve PO" outer tab / "Raise PO" inner tab are both default.
    await page.getByLabel('Supplier Name').click();
    await page.getByRole('option', { name: supplierName, exact: true }).click();

    await page.getByLabel('Drug Name').fill(productName);
    await page.getByRole('option', { name: new RegExp(productName) }).click();
    await page.getByLabel('Packing').fill('1');
    await page.getByLabel('Qty', { exact: true }).fill('10');
    await page.getByLabel('Total Quantity').fill('10');
    await page.getByRole('button', { name: 'Add', exact: true }).click();

    await expect(page.getByRole('cell', { name: productName, exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Submit' }).click();

    // Submitting immediately opens a "Quotation Form" print dialog - close it before moving on.
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByRole('heading', { name: 'Quotation Form' })).toBeVisible();
    await dialog.getByRole('button', { name: 'Done' }).click();
  });

  await test.step('approve the Purchase Order', async () => {
    await page.getByRole('tab', { name: 'Pending Approval' }).click();
    await expect(page.getByRole('cell', { name: supplierName, exact: true })).toBeVisible();
    await page
      .getByRole('row', { name: new RegExp(supplierName) })
      .getByRole('button', { name: 'View' })
      .click();

    let dialog = page.getByRole('dialog');
    await dialog.getByRole('button', { name: 'Approve', exact: true }).click();

    dialog = page.getByRole('dialog');
    await expect(dialog.getByRole('heading', { name: 'Purchase Order' })).toBeVisible();
    await dialog.getByRole('button', { name: 'Done' }).click();
  });

  await test.step('receive stock via Direct GRN', async () => {
    await page.goto('/pharmacy/purchase-management');
    await page.getByRole('tab', { name: 'Goods Receipt (GRN)' }).click();
    // "Direct GRN" inner tab is default. Scoped to .grn-form because the
    // "Raise PO" tab (with its own Supplier Name/Drug Name fields) stays
    // mounted in the DOM once visited, same collision as the master tabs.
    const grnForm = page.locator('.grn-form');
    await grnForm.getByLabel('Supplier Name').click();
    await page.getByRole('option', { name: supplierName, exact: true }).click();
    await grnForm.getByLabel('Invoice No').fill(`INV-${unique}`);
    await grnForm.getByLabel('Invoice Date').fill(todayStr);
    await grnForm.getByLabel('GRN Date').fill(todayStr);

    await grnForm.getByLabel('Drug Name').fill(productName);
    await page.getByRole('option', { name: new RegExp(productName) }).click();
    await grnForm.getByLabel('Packing').fill('1');
    await grnForm.getByLabel('Qty', { exact: true }).fill('10');
    await grnForm.getByLabel('Total Qty').fill('10');
    await grnForm.getByLabel('Batch').fill(batch);
    await grnForm.getByLabel('MRP').fill('50');
    await grnForm.getByLabel('Pur.Rate').fill('30');
    await grnForm.getByRole('button', { name: 'Add', exact: true }).click();

    await expect(grnForm.getByRole('cell', { name: productName, exact: true })).toBeVisible();
    await grnForm.getByRole('button', { name: 'Approve', exact: true }).click();
  });

  await test.step('register a patient to bill against the new stock', async () => {
    await page.goto('/registration/patients');
    await page.getByRole('button', { name: 'Add Patient' }).click();
    const dialog = page.getByRole('dialog');
    await dialog.getByLabel('Patient name').fill(patientName);
    await dialog.getByLabel('Gender').click();
    await page.getByRole('option', { name: 'Female', exact: true }).click();
    await dialog.getByLabel('Age').fill('35');
    await dialog.getByLabel('Mobile number').fill(mobileNumber);
    await dialog.getByLabel('Location').fill('Playwright Test Address');
    await dialog.getByLabel('Registered Via').click();
    await page.getByRole('option', { name: 'Front Office' }).click();
    await dialog.getByRole('button', { name: 'Add patient' }).click();
    await expect(page.getByRole('cell', { name: patientName, exact: true })).toBeVisible();
  });

  await test.step('bill the product as a Pharmacy Billing "Others" walk-in sale', async () => {
    await page.goto('/pharmacy/billing');
    await page.locator('.location-tile').first().click();

    await page.getByLabel(/search by patient id, name or mobile number/i).fill(mobileNumber);
    await page.getByRole('button', { name: 'Search' }).click();
    await page.getByRole('button', { name: 'Select' }).click();

    await page.getByRole('button', { name: 'New Bill (Others)' }).click();

    await page.getByLabel('Product Name').fill(productName);
    await page.getByRole('option', { name: new RegExp(productName) }).click();
    await page.getByRole('button', { name: 'Add', exact: true }).click();

    await expect(page.getByRole('cell', { name: productName, exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Final Approve' }).click();

    const dialog = page.getByRole('dialog');
    await expect(dialog.getByRole('heading', { name: 'Pharmacy Bill' })).toBeVisible();
    // .bill-meta contains both "Name: ..." and "Invoice No: ..." in sibling
    // spans - target the span itself, not the whole div (the patient name
    // here also contains the unique timestamp, so a whole-div text scrape
    // would concatenate both and corrupt the extracted number).
    const invoiceNoText = await dialog.locator('span', { hasText: 'Invoice No' }).innerText();
    billNumber = invoiceNoText.replace(/\D/g, '');
    expect(billNumber.length).toBeGreaterThan(0);
    await dialog.getByRole('button', { name: 'Done' }).click();
  });

  await test.step('submit a Sales Return against that invoice', async () => {
    await page.goto('/pharmacy/returns/new');
    await page.getByLabel('Invoice Number').fill(billNumber);
    await page.getByRole('button', { name: 'Submit' }).click();

    await expect(page.getByText(productName)).toBeVisible();
    const row = page.getByRole('row', { name: new RegExp(productName) });
    await row.getByRole('spinbutton').fill('1');
    await row.getByRole('button', { name: 'Return' }).click();

    await expect(page.getByRole('button', { name: 'Approve Return' })).toBeEnabled();
    await page.getByRole('button', { name: 'Approve Return' }).click();

    const dialog = page.getByRole('dialog');
    await expect(dialog.getByRole('heading', { name: 'Sales Return' })).toBeVisible();
    await expect(dialog.getByText('PENDING')).toBeVisible();
    await dialog.getByRole('button', { name: 'Done' }).click();
  });

  await test.step('approve the Sales Return', async () => {
    await page.goto('/pharmacy/returns/approval');
    await expect(page.getByRole('cell', { name: patientName, exact: true })).toBeVisible();
    await page
      .getByRole('row', { name: new RegExp(patientName) })
      .getByRole('button', { name: 'View' })
      .click();

    let dialog = page.getByRole('dialog');
    await dialog.getByRole('button', { name: 'Approve Return' }).click();

    dialog = page.getByRole('dialog');
    await expect(dialog.getByRole('heading', { name: 'Sales Return' })).toBeVisible();
    await expect(dialog.getByText('APPROVED')).toBeVisible();
    await dialog.getByRole('button', { name: 'Done' }).click();

    await expect(page.getByRole('cell', { name: patientName, exact: true })).not.toBeVisible();
  });

  await test.step('confirm the approved return appears in the Sales Return Report', async () => {
    await page.goto('/pharmacy/reports');
    await page.getByRole('tab', { name: 'Sales Return Report' }).click();
    // Scoped to the active tabpanel - the Sales Report tab's inactive panel
    // also lists this patient (it shows the underlying Pharmacy Billing sale).
    await expect(page.getByRole('tabpanel', { name: 'Sales Return Report' }).getByText(patientName)).toBeVisible();
  });
});
