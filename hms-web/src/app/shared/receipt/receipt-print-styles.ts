import { PRINT_TABLE_HEADER_CSS } from '../print/print-style-tokens';

/**
 * Plain CSS for a receipt printed via a standalone popup window (see
 * AppointmentBillingDialogComponent.print() / RefundReceiptDialogComponent.print()).
 * The popup is a brand-new document with none of the app's compiled component
 * styles or CSS custom properties available, so this mirrors shared/receipt/_receipt.scss's
 * .receipt-* rules with hardcoded values instead of var(--hms-*) tokens.
 */
export const RECEIPT_PRINT_STYLES = `
  body {
    font-family: Arial, Helvetica, sans-serif;
    color: #1a1a1a;
    margin: 24px;
  }
  .receipt {
    border: 1px solid #ccc;
    border-radius: 8px;
    padding: 16px;
  }
  .receipt-header {
    position: relative;
    min-height: 64px;
    text-align: center;
    padding-bottom: 12px;
    border-bottom: 1px solid #ccc;
  }
  .receipt-logo {
    position: absolute;
    top: 0;
    left: 0;
    width: 64px;
    height: 64px;
    object-fit: contain;
  }
  .receipt-clinic-info {
    display: flex;
    flex-direction: column;
  }
  .receipt-clinic-name {
    font-size: 1.1rem;
  }
  .receipt-meta {
    display: flex;
    justify-content: space-between;
    font-size: 0.9rem;
    margin-top: 8px;
  }
  .receipt-items-table {
    width: 100%;
    border-collapse: collapse;
    margin-top: 8px;
  }
  .receipt-items-table th,
  .receipt-items-table td {
    text-align: left;
    padding: 8px;
  }
  .receipt-items-table th {
    ${PRINT_TABLE_HEADER_CSS}
  }
  .receipt-items-table th:last-child,
  .receipt-items-table td:last-child {
    text-align: right;
  }
  .receipt-total-row {
    font-weight: 600;
    border-top: 1px solid #ccc;
  }
  .receipt-words,
  .receipt-remarks {
    margin: 8px 0 0;
    font-size: 0.9rem;
  }
  .receipt-signature {
    text-align: right;
    margin-top: 32px;
  }
  @media print {
    body {
      margin: 0;
    }
    .receipt {
      border: none;
    }
  }
`;
