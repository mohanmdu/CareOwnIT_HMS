/**
 * Dot Matrix bill layout matching the legacy reference exactly - plain CSS
 * for a standalone popup print window (see receipt-print-styles.ts's doc
 * comment for why this can't reuse CSS custom properties).
 */
export const PHARMACY_BILL_PRINT_STYLES = `
  body {
    font-family: Consolas, 'Courier New', monospace;
    color: #000;
    margin: 16px;
    font-size: 12px;
  }
  .bill {
    border: 1px solid #000;
    padding: 12px;
  }
  .bill-header {
    text-align: center;
    font-weight: 700;
    font-size: 14px;
    border-bottom: 1px solid #000;
    padding-bottom: 6px;
    margin-bottom: 6px;
  }
  .bill-header-row {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
  }
  .bill-title {
    text-align: center;
    font-weight: 700;
    text-decoration: underline;
    margin: 6px 0;
  }
  .bill-meta {
    display: flex;
    justify-content: space-between;
    margin: 2px 0;
  }
  .bill-items-table {
    width: 100%;
    border-collapse: collapse;
    margin-top: 6px;
  }
  .bill-items-table th,
  .bill-items-table td {
    border: 1px solid #000;
    padding: 3px 4px;
    text-align: left;
  }
  .bill-items-table th {
    font-weight: 700;
  }
  .bill-items-table th:last-child,
  .bill-items-table td:last-child {
    text-align: right;
  }
  .bill-totals {
    display: flex;
    justify-content: space-between;
    margin-top: 6px;
  }
  .bill-words {
    margin: 6px 0 0;
  }
  .bill-signature {
    text-align: right;
    margin-top: 24px;
  }
  .bill-footer {
    text-align: center;
    margin-top: 12px;
    font-size: 11px;
  }
  @media print {
    body { margin: 0; }
    .bill { border: none; }
  }
`;
