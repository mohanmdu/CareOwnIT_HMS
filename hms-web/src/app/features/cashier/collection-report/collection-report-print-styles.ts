/**
 * Plain CSS for the Collection Report printed via a standalone popup window
 * (see CollectionReportComponent.print()) - same pattern as
 * appointments/reports/report-print-styles.ts, since a popup document has
 * none of the app's compiled styles or CSS custom properties available.
 */
export const COLLECTION_REPORT_PRINT_STYLES = `
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
    font-size: 0.8rem;
  }
  th, td {
    border: 1px solid #ccc;
    padding: 6px 8px;
    text-align: left;
    white-space: nowrap;
  }
  th {
    background: #f2f2f2;
  }
  .collection-report-totals {
    display: flex;
    flex-wrap: wrap;
    gap: 24px;
    margin-top: 16px;
    font-size: 0.85rem;
  }
  .collection-report-totals strong {
    font-size: 1rem;
  }
`;
