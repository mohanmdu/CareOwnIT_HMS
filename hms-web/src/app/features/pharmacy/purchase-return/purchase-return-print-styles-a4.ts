import { PRINT_TABLE_HEADER_CSS } from '../../../shared/print/print-style-tokens';

/**
 * A4 Purchase Return Receipt layout - spacious letterhead-style print,
 * distinct from the Dot Matrix format's monospace/bordered look.
 */
export const PURCHASE_RETURN_PRINT_STYLES_A4 = `
  body {
    font-family: Arial, Helvetica, sans-serif;
    color: #1a1a1a;
    margin: 32px;
  }
  .purchase-return-receipt {
    max-width: 210mm;
    margin: 0 auto;
  }
  .purchase-return-header-row {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    border-bottom: 2px solid #1a1a1a;
    padding-bottom: 12px;
    margin-bottom: 12px;
  }
  .purchase-return-title {
    text-align: center;
    font-size: 1.4rem;
    font-weight: 700;
    margin: 12px 0;
  }
  .purchase-return-meta {
    display: flex;
    justify-content: space-between;
    margin: 4px 0;
  }
  .purchase-return-supplier-card {
    border: 1px solid #ccc;
    border-radius: 6px;
    padding: 10px 14px;
    margin: 10px 0;
  }
  .purchase-return-items-table {
    width: 100%;
    border-collapse: collapse;
    margin-top: 12px;
  }
  .purchase-return-items-table th,
  .purchase-return-items-table td {
    border: 1px solid #ccc;
    padding: 8px 10px;
    text-align: left;
  }
  .purchase-return-items-table th {
    ${PRINT_TABLE_HEADER_CSS}
  }
  .purchase-return-items-table th:last-child,
  .purchase-return-items-table td:last-child {
    text-align: right;
  }
  .purchase-return-totals {
    display: flex;
    justify-content: flex-end;
    margin-top: 10px;
    font-size: 1.05rem;
    font-weight: 700;
  }
  .purchase-return-signature {
    text-align: right;
    margin-top: 48px;
  }
`;
