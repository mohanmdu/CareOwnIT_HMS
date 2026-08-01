export const environment = {
  production: true,
  apiBaseUrl: '/api',
  // Base URL of the internal HMS app (hms-web) this hospital instance runs -
  // "Book Appointment" links out to its booking module rather than
  // duplicating anonymous booking logic on the public site. Set per
  // deployment (e.g. a subdomain or path the reverse proxy routes to hms-web).
  // Local path-based value, uncomment to test that routing style again:
  // hmsAppUrl: '/hms'
  hmsAppUrl: 'https://admin.careownitsolutions.com'
};
