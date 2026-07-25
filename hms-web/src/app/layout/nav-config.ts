import type { ModuleKey } from './package-config';

export interface NavItem {
  label: string;
  route: string;
  icon: string;
  /** Overrides the parent group's moduleKey for package-tier filtering - see getVisibleNavGroups(). Only needed when a group mixes items from different tiers, e.g. Overview below. */
  moduleKey?: ModuleKey;
}

export interface NavGroup {
  label: string;
  moduleKey: ModuleKey;
  /** Shown on the group's own collapsible header row (or the flat row, for a single-item group). */
  icon: string;
  items: NavItem[];
}

/**
 * Grouped primary navigation shown in the app shell sidenav. Mirrors the
 * feature module boundaries in src/app/features (migration doc §6.1).
 *
 * Group ORDER follows the sidebar redesign requested 2026-07-24: Overview,
 * Patient Registration, Appointments / Direct Visit, IP Admission Module,
 * Room / Ward Management, Lab & Investigations, Upload Reports, Pharmacy,
 * Insurance Module, IP Billing Master, Masters, Administration, Website CMS -
 * with three groups that request didn't mention (Discharge Summary Module,
 * ICD Codes Module, the general Billing group) kept as their own sections at
 * the most logical adjacent slot (confirmed with the user rather than
 * silently hiding them) - Discharge Summary right after IP Admission, ICD
 * Codes right after Lab & Investigations, Billing right after IP Billing
 * Master.
 *
 * Overview bundles Dashboard and Cashier Module together (both are
 * metrics-only landing screens, per the user's 2026-07-24 follow-up) rather
 * than each being its own top-level entry - its Cashier Module item carries
 * an explicit moduleKey override since Overview itself is always-on but
 * Cashier is a PREMIUM-tier feature (see package-config.ts).
 *
 * A group with exactly one item renders as a flat, non-collapsible link in
 * the shell rather than a single-child accordion - see AppShellComponent.
 * Five legacy modules with no equivalent screen yet (Pharmacy MIS, CEO/MD
 * Dashboard as its own module, FAQ/How to use HMS, OP/IP Case Sheet) are
 * intentionally left out rather than stubbed - see moduleKey/package-config.ts
 * for how this list is filtered per package tier.
 */
/** All NavItem routes whose effective moduleKey (item override, or its group's) equals the given key - used for the Role permission picker's per-module submenu, and for "has this module's page-level access ever been restricted" checks (see package-config.ts / auth.guard.ts). */
export function routesForModule(moduleKey: ModuleKey): string[] {
  const routes: string[] = [];
  for (const group of NAV_GROUPS) {
    for (const item of group.items) {
      if ((item.moduleKey ?? group.moduleKey) === moduleKey) {
        routes.push(item.route);
      }
    }
  }
  return routes;
}

export const NAV_GROUPS: NavGroup[] = [
  {
    label: 'Overview',
    moduleKey: 'overview',
    icon: 'dashboard',
    items: [
      { label: 'Dashboard', route: '/dashboard', icon: 'dashboard' },
      { label: 'CEO/MD Dashboard', route: '/ceo-dashboard', icon: 'insights', moduleKey: 'ceo-dashboard' },
      { label: 'Cashier Module', route: '/cashier/dashboard', icon: 'point_of_sale', moduleKey: 'cashier' }
    ]
  },
  {
    label: 'Patient Registration',
    moduleKey: 'patient-registration',
    icon: 'how_to_reg',
    items: [
      { label: 'Patients', route: '/registration/patients', icon: 'person_add' },
      { label: 'Patient Information (OP/IP)', route: '/registration/patients/history', icon: 'history_edu' },
      { label: 'Review Date Report', route: '/registration/patients/review-date-report', icon: 'event_repeat' },
      { label: 'Logs', route: '/registration/patients/logs', icon: 'history' }
    ]
  },
  {
    label: 'Appointments / Direct Visit',
    moduleKey: 'appointments',
    icon: 'calendar_month',
    items: [
      { label: 'Book Appointment', route: '/appointments/book', icon: 'event_available' },
      { label: 'OP Direct Billing', route: '/appointments/direct-billing', icon: 'point_of_sale' },
      { label: 'Appointments', route: '/appointments', icon: 'event' },
      { label: 'Patient Prescription', route: '/appointments/prescriptions', icon: 'description' },
      { label: 'Collection Report', route: '/appointments/reports/collection', icon: 'summarize' },
      { label: 'Refund & Report', route: '/appointments/refunds', icon: 'currency_exchange' },
      { label: 'Appointment Audit Logs', route: '/appointments/audit-logs', icon: 'history' }
    ]
  },
  {
    label: 'IP Admission Module',
    moduleKey: 'ip-admission',
    icon: 'assignment_ind',
    items: [
      { label: 'New Admission', route: '/ip/admissions/new', icon: 'person_add' },
      { label: 'IP Admissions', route: '/ip/admissions', icon: 'local_hospital' },
      { label: 'In Patient List', route: '/ip/inpatient-list', icon: 'groups' },
      { label: 'Discharge List', route: '/ip/discharge-list', icon: 'assignment_turned_in' }
    ]
  },
  {
    label: 'Discharge Summary Module',
    moduleKey: 'discharge-summary',
    icon: 'exit_to_app',
    items: [
      { label: 'Discharge Initiated List', route: '/ip/discharge-summary/initiated', icon: 'pending_actions' },
      { label: 'Discharge List', route: '/ip/discharge-summary', icon: 'assignment' }
    ]
  },
  {
    label: 'Room / Ward Management',
    moduleKey: 'room-ward',
    icon: 'king_bed',
    items: [
      { label: 'Room Types', route: '/ip/room-types', icon: 'apartment' },
      { label: 'Room Numbers', route: '/ip/rooms', icon: 'bed' },
      { label: 'Room Availability', route: '/ip/room-availability', icon: 'meeting_room' }
    ]
  },
  {
    label: 'Lab & Investigations Module',
    moduleKey: 'lab',
    icon: 'biotech',
    items: [
      { label: 'Lab Requisition', route: '/lab/requisitions', icon: 'assignment_add' },
      { label: 'Lab & X-Ray/Scan Billing', route: '/lab/billing', icon: 'point_of_sale' },
      { label: 'Lab Entry Queue & Report', route: '/lab/test-entries', icon: 'fact_check' },
      { label: 'Lab Draft Report', route: '/lab/test-entries/draft', icon: 'drafts' },
      { label: 'Lab Approved Report/Reports Share to WhatsApp', route: '/lab/test-entries/approved', icon: 'verified' },
      { label: 'Lab Category', route: '/lab/masters/categories', icon: 'category' },
      { label: 'Lab Sub-Category', route: '/lab/masters/sub-categories', icon: 'list_alt' },
      { label: 'Lab Component', route: '/lab/masters/components', icon: 'science' },
      { label: 'Summary Collection Report', route: '/lab/reports/summary-collection', icon: 'summarize' },
      { label: 'Lab Detail Collection Report', route: '/lab/reports/lab-detail-collection', icon: 'receipt_long' },
      {
        label: 'Investigation Detail Collection Report',
        route: '/lab/reports/investigation-detail-collection',
        icon: 'receipt_long'
      },
      { label: 'Payment Refund', route: '/lab/refunds', icon: 'currency_exchange' },
      { label: 'Refund Report', route: '/lab/reports/refund', icon: 'assignment_return' },
      { label: 'Cancelled Report', route: '/lab/reports/cancelled', icon: 'event_busy' }
    ]
  },
  {
    label: 'ICD Codes Module',
    moduleKey: 'icd-codes',
    icon: 'tag',
    items: [
      { label: 'ICD Code Search', route: '/icd/search', icon: 'travel_explore' },
      { label: 'ICD Code Master', route: '/icd/master', icon: 'menu_book' }
    ]
  },
  {
    label: 'Upload Reports',
    moduleKey: 'upload-reports',
    icon: 'cloud_upload',
    items: [
      { label: 'Upload Reports', route: '/patient-reports/upload', icon: 'upload_file' },
      { label: 'View Files/Reports share to WhatsApp', route: '/patient-reports/view', icon: 'folder_shared' },
      { label: 'Audit Log', route: '/patient-reports/audit-log', icon: 'fact_check' }
    ]
  },
  {
    label: 'Pharmacy',
    moduleKey: 'pharmacy',
    icon: 'medication',
    items: [
      { label: 'Inventory Master', route: '/pharmacy/inventory-master', icon: 'inventory_2' },
      { label: 'Purchase Management', route: '/pharmacy/purchase-management', icon: 'shopping_cart' },
      { label: 'Pharmacy Billing', route: '/pharmacy/billing', icon: 'point_of_sale' },
      { label: 'Sales Return', route: '/pharmacy/returns/new', icon: 'assignment_return' },
      { label: 'Sales Return Approval', route: '/pharmacy/returns/approval', icon: 'fact_check' },
      { label: 'Stock Adjustment', route: '/pharmacy/stock-adjustment', icon: 'tune' },
      { label: 'Purchase Return', route: '/pharmacy/purchase-return', icon: 'undo' },
      { label: 'Pharmacy Reports', route: '/pharmacy/reports', icon: 'summarize' }
    ]
  },
  {
    label: 'Insurance Module',
    moduleKey: 'insurance',
    icon: 'shield',
    items: [
      { label: 'Pre Authorization Requests', route: '/insurance/pre-authorization', icon: 'health_and_safety' },
      { label: 'Insurance Approval Queue', route: '/insurance/approval-queue', icon: 'fact_check' },
      { label: 'Insurance Claim Report', route: '/insurance/claim-report', icon: 'request_quote' },
      { label: 'Insurance Rejected Report', route: '/insurance/rejected-report', icon: 'cancel' },
      { label: 'Insurance Companies', route: '/insurance/companies', icon: 'domain' }
    ]
  },
  {
    label: 'IP Billing Master',
    moduleKey: 'ip-billing-master',
    icon: 'account_balance',
    items: [
      { label: 'IP Billing Categories', route: '/masters/ip-billing-categories', icon: 'category' },
      { label: 'IP Billing Components', route: '/masters/ip-billing-components', icon: 'sell' },
      { label: 'Billing Activity Log', route: '/masters/ip-billing-activity-log', icon: 'history' },
      { label: 'Consultant Wise Report', route: '/ip/reports/consultant-wise', icon: 'summarize' },
      { label: 'Admission Report', route: '/ip/reports/admission', icon: 'receipt_long' },
      { label: 'Advance Report', route: '/ip/reports/advance', icon: 'payments' },
      { label: 'Advance Cancel', route: '/ip/reports/advance-cancel', icon: 'cancel' },
      { label: 'Cancelled Admissions', route: '/ip/reports/cancelled-admissions', icon: 'event_busy' },
      { label: 'IP/OP Tracking Report', route: '/ip/reports/activity-log', icon: 'manage_search' }
    ]
  },
  {
    label: 'Billing',
    moduleKey: 'billing',
    icon: 'receipt_long',
    items: [
      { label: 'Billing Catalog', route: '/billing/catalog', icon: 'sell' },
      { label: 'New Invoice', route: '/billing/invoices/new', icon: 'receipt_long' },
      { label: 'Invoices', route: '/billing/invoices', icon: 'request_quote' }
    ]
  },
  {
    label: 'Masters',
    moduleKey: 'masters',
    icon: 'settings_applications',
    items: [
      { label: 'Departments', route: '/masters/departments', icon: 'apartment' },
      { label: 'Consultants', route: '/masters/consultants', icon: 'medical_services' },
      { label: 'Specializations', route: '/masters/specializations', icon: 'workspace_premium' },
      { label: 'OP Billing Categories', route: '/masters/op-billing-categories', icon: 'category' },
      { label: 'OP Billing Components', route: '/masters/op-billing-components', icon: 'sell' }
    ]
  },
  {
    label: 'Administration',
    moduleKey: 'administration',
    icon: 'manage_accounts',
    items: [
      { label: 'Roles', route: '/masters/roles', icon: 'admin_panel_settings' },
      { label: 'General Users', route: '/masters/general-users', icon: 'group' },
      { label: 'Master Audit Logs', route: '/masters/general-users/logs', icon: 'history' },
      { label: 'Clinic Settings', route: '/masters/clinic-settings', icon: 'settings' }
    ]
  },
  {
    label: 'Website CMS',
    moduleKey: 'website-cms',
    icon: 'language',
    items: [
      { label: 'Site Content', route: '/masters/cms/site-content', icon: 'article' },
      { label: 'Banner Slides', route: '/masters/cms/banner-slides', icon: 'wallpaper' },
      { label: 'Gallery', route: '/masters/cms/gallery-items', icon: 'photo_library' },
      { label: 'News & Events', route: '/masters/cms/news-events', icon: 'campaign' },
      { label: 'Testimonials', route: '/masters/cms/testimonials', icon: 'format_quote' },
      { label: 'FAQs', route: '/masters/cms/faqs', icon: 'quiz' },
      { label: 'Health Packages', route: '/masters/cms/health-packages', icon: 'medical_information' },
      { label: 'Blog Posts', route: '/masters/cms/blog-posts', icon: 'article' },
      { label: 'Career Openings', route: '/masters/cms/career-openings', icon: 'work' }
    ]
  }
];
