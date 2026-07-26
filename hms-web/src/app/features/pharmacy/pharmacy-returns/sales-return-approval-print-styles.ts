import { PRINT_TABLE_HEADER_CSS } from '../../../shared/print/print-style-tokens';

/**
 * Plain CSS for the Sales Return Approval list printed via a standalone
 * popup window, same pattern as refund-report-print-styles.ts.
 */
export const SALES_RETURN_APPROVAL_PRINT_STYLES = `
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
`;
