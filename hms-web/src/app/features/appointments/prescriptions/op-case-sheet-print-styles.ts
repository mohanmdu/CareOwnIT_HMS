/**
 * Plain CSS for the OP Case Sheet printed via a standalone popup window (see
 * OpCaseSheetViewDialogComponent.print()) - same reasoning and pattern as
 * shared/receipt/receipt-print-styles.ts.
 */
export const OP_CASE_SHEET_PRINT_STYLES = `
  body {
    font-family: Arial, Helvetica, sans-serif;
    color: #1a1a1a;
    margin: 24px;
    font-size: 0.85rem;
  }
  h3, h4 {
    margin: 16px 0 4px;
  }
  .receipt-header {
    position: relative;
    min-height: 64px;
    text-align: center;
    padding-bottom: 12px;
    border-bottom: 1px solid #ccc;
    margin-bottom: 12px;
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
  table {
    width: 100%;
    border-collapse: collapse;
    margin-bottom: 8px;
  }
  .case-sheet-header-table td {
    padding: 2px 8px;
  }
  .case-sheet-items-table th,
  .case-sheet-items-table td {
    border: 1px solid #ccc;
    padding: 4px 8px;
    text-align: left;
  }
  .case-sheet-items-table th {
    font-weight: 700;
  }
  .case-sheet-field-row {
    display: flex;
    gap: 24px;
    margin: 2px 0;
  }
  .case-sheet-field-row span {
    flex: 1;
  }
`;
