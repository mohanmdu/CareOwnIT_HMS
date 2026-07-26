import { PRINT_TABLE_HEADER_CSS } from '../../../shared/print/print-style-tokens';

/**
 * Plain CSS for the Refund Report printed via a standalone popup window
 * (see RefundReportComponent.print()) - same pattern as
 * shared/receipt/receipt-print-styles.ts and reports/report-print-styles.ts.
 */
export const REFUND_REPORT_PRINT_STYLES = `
  body {
    font-family: Arial, Helvetica, sans-serif;
    color: #1a1a1a;
    margin: 24px;
  }
  h1 {
    font-size: 1.25rem;
    margin: 0 0 16px;
  }
  table {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.85rem;
  }
  th, td {
    border: 1px solid #ccc;
    padding: 6px 8px;
    text-align: left;
    white-space: nowrap;
  }
  th {
    ${PRINT_TABLE_HEADER_CSS}
  }
  .refund-report-summary {
    display: flex;
    gap: 24px;
    margin-top: 16px;
  }
  .refund-report-summary-item {
    display: flex;
    flex-direction: column;
    font-size: 0.85rem;
  }
  .refund-report-summary-item strong {
    font-size: 1rem;
  }
  .refund-report-invoice-link {
    background: none;
    border: none;
    padding: 0;
    font: inherit;
    color: inherit;
    text-decoration: none;
    cursor: default;
  }
`;
