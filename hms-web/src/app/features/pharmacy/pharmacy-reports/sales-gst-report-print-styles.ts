import { PRINT_TABLE_HEADER_CSS } from '../../../shared/print/print-style-tokens';

export const SALES_GST_REPORT_PRINT_STYLES = `
  body {
    font-family: Arial, Helvetica, sans-serif;
    color: #1a1a1a;
    margin: 24px;
  }
  h1, h2, h3 {
    font-size: 1.05rem;
    margin: 16px 0 8px;
  }
  table {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.8rem;
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
