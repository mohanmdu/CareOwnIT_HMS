import { PRINT_TABLE_HEADER_CSS } from '../../../shared/print/print-style-tokens';

/**
 * Plain CSS for the Collection Report printed via a standalone popup window
 * (see CollectionReportComponent.print()) - same pattern as
 * receipt-print-styles.ts, since a popup document has none of the app's
 * compiled styles or CSS custom properties available.
 */
export const REPORT_PRINT_STYLES = `
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
  .collection-report-summary {
    display: flex;
    gap: 24px;
    margin-top: 16px;
  }
  .collection-report-summary-item {
    display: flex;
    flex-direction: column;
    font-size: 0.85rem;
  }
  .collection-report-summary-item strong {
    font-size: 1rem;
  }
  .collection-report-invoice-link {
    background: none;
    border: none;
    padding: 0;
    font: inherit;
    color: inherit;
    text-decoration: none;
    cursor: default;
  }
`;
