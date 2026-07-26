import { PRINT_TABLE_HEADER_CSS } from '../../../shared/print/print-style-tokens';

/**
 * Plain CSS for the Consultant Wise Report printed via a standalone popup
 * window (see ConsultantWiseReportComponent.print()) - same reasoning as
 * appointments/reports/report-print-styles.ts: a popup document has none of
 * the app's compiled styles or CSS custom properties available.
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
    font-size: 0.9rem;
  }
  th, td {
    border: 1px solid #ccc;
    padding: 8px 10px;
    text-align: left;
  }
  td:last-child, th:last-child {
    text-align: right;
  }
  th {
    ${PRINT_TABLE_HEADER_CSS}
  }
  .cwr-total-row,
  tfoot tr {
    font-weight: 700;
    background: #f0f0f0;
  }
  .ar2-letterhead,
  .dl-letterhead {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    padding-bottom: 12px;
    margin-bottom: 12px;
    border-bottom: 2px solid #ccc;
  }
  .ar2-logo,
  .dl-logo {
    width: 56px;
    height: 56px;
    border-radius: 50%;
    object-fit: cover;
  }
  .ar2-title,
  .dl-title {
    flex: 1;
    text-align: center;
    font-size: 1.3rem;
    font-weight: 700;
  }
  .ar2-contact,
  .dl-contact {
    display: flex;
    flex-direction: column;
    text-align: right;
    font-size: 0.8rem;
  }
`;
