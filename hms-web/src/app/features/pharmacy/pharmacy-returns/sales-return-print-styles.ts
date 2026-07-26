/**
 * Dot Matrix Sales Return receipt layout, same plain-CSS popup-window
 * pattern as pharmacy-bill-print-styles.ts (that file's doc comment explains
 * why this can't reuse the app's CSS custom properties).
 */
export const SALES_RETURN_PRINT_STYLES = `
  body {
    font-family: Consolas, 'Courier New', monospace;
    color: #000;
    margin: 16px;
    font-size: 12px;
  }
  .return-slip {
    border: 1px solid #000;
    padding: 12px;
  }
  .return-slip-header {
    text-align: center;
    font-weight: 700;
    font-size: 14px;
    border-bottom: 1px solid #000;
    padding-bottom: 6px;
    margin-bottom: 6px;
  }
  .return-slip-header-row {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
  }
  .return-slip-title {
    text-align: center;
    font-weight: 700;
    text-decoration: underline;
    margin: 6px 0;
  }
  .return-slip-meta {
    display: flex;
    justify-content: space-between;
    margin: 2px 0;
  }
  .return-slip-items-table {
    width: 100%;
    border-collapse: collapse;
    margin-top: 6px;
  }
  .return-slip-items-table th,
  .return-slip-items-table td {
    border: 1px solid #000;
    padding: 3px 4px;
    text-align: left;
  }
  .return-slip-items-table th {
    font-weight: 700;
  }
  .return-slip-items-table th:last-child,
  .return-slip-items-table td:last-child {
    text-align: right;
  }
  .return-slip-totals {
    display: flex;
    justify-content: space-between;
    margin-top: 6px;
  }
  .return-slip-words {
    margin: 6px 0 0;
  }
  .return-slip-signature {
    text-align: right;
    margin-top: 24px;
  }
  @media print {
    body { margin: 0; }
    .return-slip { border: none; }
  }
`;
