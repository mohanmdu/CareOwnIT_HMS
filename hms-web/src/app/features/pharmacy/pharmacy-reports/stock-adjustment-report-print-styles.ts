import { PRINT_TABLE_HEADER_CSS } from '../../../shared/print/print-style-tokens';

/**
 * Plain CSS for the Stock Adjustment Report printed via a standalone popup
 * window, same pattern as sales-return-approval-print-styles.ts.
 */
export const STOCK_ADJUSTMENT_REPORT_PRINT_STYLES = `
  body {
    font-family: Arial, Helvetica, sans-serif;
    color: #1a1a1a;
    margin: 24px;
  }
  h1, h2 {
    font-size: 1.1rem;
    margin: 16px 0 8px;
  }
  table {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.85rem;
    margin-bottom: 16px;
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
`;
