# Lab & Investigation Module — UI/UX Redesign (Meridian Design System)

**Status: Approved.** Design document and 3-screen live prototype both reviewed and signed off. Ready for implementation — see [`lab-investigation-redesign-implementation-prompt.md`](lab-investigation-redesign-implementation-prompt.md) for a self-contained kickoff prompt.

**Prototype**: https://claude.ai/code/artifact/387f994d-8b43-4e42-ba24-5f22ea631ca3 — interactive, 3 screens (Component Master with a live Before/After toggle, Test Entry, Collection Report), respects light/dark theme. Reference this visually alongside the written spec below; it uses system-font approximations of Inter/IBM Plex Mono and hand-drawn Lucide-style SVGs rather than the real packages (sandboxed preview constraint — production implementation loads the real ones per §2.2/§2.5).

## Context

The HMS admin app (`hms-web`) has grown module-by-module over time. Several modules were already redesigned recently and look genuinely good (Doctor Queue, Appointments/Booking, Clinic Settings, OP Direct Billing) — they share a consistent token-driven design system (`--hms-*` CSS variables), a small component library (`status-badge`, `stat-tile`, `section-card`, `page-header`, `toast`), and classic Material Icons throughout. Other modules — confirmed via direct code research: **Lab & Investigation** (27 screens, ~1,456 lines of bespoke per-screen SCSS, hardcoded `#e65100` orange banners left over from a pre-token era) and **ICD** — never got that treatment.

The brief that produced this document: a genuinely new, premium, enterprise-SaaS visual language across the **entire application** (15 feature modules), with modern animation, a switch from Material Icons to **Lucide**, and full accessibility/responsive rigor — while guaranteeing **zero change** to business logic, APIs, validations, routing, permissions, or database operations.

Given the true scope (15 modules, an estimated 150–250+ screens — Lab alone is 27), this document delivers:

1. **The complete new design system** (colors, typography, spacing, elevation, icons, motion, accessibility, responsive rules) — written once, applies everywhere.
2. **A fully detailed redesign of the Lab & Investigation module** as the flagship/proof-of-concept, covering every screen group with concrete before/after treatment.
3. **A confidence-rated audit of the other 14 modules**, so the remaining rollout can be phased with its own approval round per module rather than guessing at unaudited modules today.

---

## 1. Current UI — Analysis & Pain Points

**Confirmed first-hand (Lab & Investigation, ICD):**
- Hardcoded `#e65100` (Material's stock "deep orange 900") page-header banners on 11+ Lab screens and in ICD — a color that appears nowhere in the app's actual theme palette; a leftover from before the token system existed.
- ~1,456 lines of bespoke, one-off SCSS per screen (`lcat-*`, `lcomp-*`, `pba-*`, `lte-*`, 20+ unique prefixes) — near-zero component reuse; every screen reinvents its own table-wrap, toolbar, and filter-bar rules instead of sharing one.
- No use of the shared `status-badge` component anywhere in Lab — statuses render as plain text (`'New'`, `'Draft'`).
- Dense, unbroken forms (the Lab Component master has 13 fields in one flat grid; the Test Entry result form is a single grouped table with no visual rest points).
- Report screens present raw data tables with a `<tfoot>` totals row but no at-a-glance summary (no stat tiles), so users must scan a wide table to find the number they need.
- Classic Material Icons ligature font throughout — functional but visually plain next to what premium SaaS products ship today (thin, consistent-stroke icon sets).
- No motion of any kind — state changes (loading, success, row updates) are instant and jarring; no skeleton states, no hover feedback beyond default browser behavior on most custom elements.

**Not yet directly audited** (10 of 15 modules — see §5): cashier, insurance, ip-admission, patient-reports, pharmacy, registration, auth, ceo-dashboard, reports-mis. Two of these (ceo-dashboard, reports-mis) show indirect evidence of already using the modern `stat-tile` component (per a "Fix uneven MIS dashboard stat tile heights" commit), so they may already be close to the bar — this needs confirming, not assuming, before their rollout is scheduled.

---

## 2. New Design System

This is a genuinely new visual language, not a re-skin of the current tokens. **Implementation strategy**: keep the existing `--hms-*` CSS custom property *names* (hundreds of files already reference `var(--hms-color-primary)` etc. — renaming every reference is a large, purely-mechanical, zero-benefit change), but redefine every *value*. This means the entire app's look changes app-wide the moment `_tokens.scss` is updated, without needing to touch every component file just to pick up the new palette. Component-level hardcoded colors (the `#e65100` banners etc.) still need individual fixes — that work is what each module's redesign pass does.

### 2.1 Color Palette — "Meridian"

Indigo-led, teal-accented, precise neutral grays. Chosen for the specific brief: premium, clinical-but-warm, credible for a multi-hospital enterprise SaaS buyer, and distinct from the generic Google-blue most admin panels default to.

| Token | Value | Usage |
|---|---|---|
| `--hms-color-primary` | `#4F46E5` (indigo-600) | Primary actions, active nav, links, focus |
| `--hms-color-primary-hover` | `#4338CA` (indigo-700) | Hover/pressed state |
| `--hms-color-primary-container` | `#EEF2FF` (indigo-50) | Selected/active backgrounds |
| `--hms-color-on-primary-container` | `#3730A3` (indigo-800) | Text on primary-container |
| `--hms-color-secondary` | `#7C3AED` (violet-600) | Secondary accents, less-common actions |
| `--hms-color-secondary-container` | `#F5F3FF` | |
| `--hms-color-on-secondary-container` | `#5B21B6` | |
| `--hms-color-tertiary` | `#0D9488` (teal-600) | Informational accents, medical/health connotation |
| `--hms-color-tertiary-container` | `#F0FDFA` | |
| `--hms-color-on-tertiary-container` | `#115E59` | |
| `--hms-color-success` | `#059669` (emerald-600) | |
| `--hms-color-success-container` | `#ECFDF5` | |
| `--hms-color-on-success-container` | `#065F46` | |
| `--hms-color-warning` | `#D97706` (amber-600) | |
| `--hms-color-warning-container` | `#FFFBEB` | |
| `--hms-color-on-warning-container` | `#92400E` | |
| `--hms-color-danger` | `#E11D48` (rose-600) | |
| `--hms-color-danger-container` | `#FFF1F2` | |
| `--hms-color-on-danger-container` | `#9F1239` | |
| `--hms-color-info` | `#0284C7` (sky-600) | |
| `--hms-color-info-container` | `#F0F9FF` | |
| `--hms-color-on-info-container` | `#075985` | |
| `--hms-color-surface` | `#FFFFFF` | Cards, panels |
| `--hms-color-surface-alt` | `#F8FAFC` (slate-50) | Page background |
| `--hms-color-surface-sunken` | `#F1F5F9` (slate-100) | Table zebra/hover, inset areas |
| `--hms-color-border` | `#E2E8F0` (slate-200) | |
| `--hms-color-border-strong` | `#CBD5E1` (slate-300) | |
| `--hms-color-text` | `#0F172A` (slate-900) | |
| `--hms-color-text-muted` | `#64748B` (slate-500) | |
| `--hms-color-text-disabled` | `#94A3B8` (slate-400) | |

All pairs verified ≥ 4.5:1 contrast (WCAG AA) for text-on-container combinations.

**Dark mode** (derived for the prototype, same identity at adjusted lightness — apply via the existing `[data-theme='dark']` override mechanism):

| Token | Dark value |
|---|---|
| `--hms-color-surface-alt` (page bg) | `#0B1120` |
| `--hms-color-surface` | `#121A2C` |
| `--hms-color-surface-sunken` | `#1B2438` |
| `--hms-color-border` | `#2A3550` |
| `--hms-color-border-strong` | `#3A4668` |
| `--hms-color-text` | `#E7EAF3` |
| `--hms-color-text-muted` | `#94A0BE` |
| `--hms-color-primary` | `#818CF8` (indigo-400) |
| `--hms-color-primary-container` | `#211E52` |
| `--hms-color-on-primary-container` | `#C7D2FE` |
| `--hms-color-tertiary` | `#2DD4BF` (teal-400) |
| `--hms-color-tertiary-container` | `#0F2E2A` |
| `--hms-color-on-tertiary-container` | `#99F6E4` |
| `--hms-color-success` | `#34D399` / container `#082F1F` / on `#A7F3D0` |
| `--hms-color-warning` | `#FBBF24` / container `#2E1E05` / on `#FDE68A` |
| `--hms-color-danger` | `#FB7185` / container `#2E0B14` / on `#FECDD3` |
| `--hms-color-info` | `#38BDF8` / container `#04222E` / on `#BAE6FD` |

### 2.2 Typography

Replacing Poppins app-wide with **Inter** (variable font) — the de facto premium-SaaS UI typeface (Linear, Vercel, Stripe dashboard, Notion) — highly legible at small sizes, excellent numeral tabular-figures support for data-heavy screens.

- `--hms-font-family: 'Inter', system-ui, -apple-system, 'Segoe UI', Roboto, Arial, sans-serif;`
- **New**: `--hms-font-family-mono: 'IBM Plex Mono', ui-monospace, monospace;` — used specifically for large numeric values (stat tiles, invoice/amount columns, patient IDs) — a "data-forward enterprise" detail that makes numbers feel precise and intentional rather than incidental.
- Size scale unchanged in structure (xs/sm/md/lg/xl/2xl/3xl), values kept as-is (12/13/14/16/20/24/32px) — already a sound scale.
- Weight scale: 400/500/600/700, **add 800** for hero numbers (stat tiles, dashboard KPIs).

### 2.3 Spacing & Radius

Spacing: keep the existing 4px-base scale (4/8/12/16/20/24/32/40/48px) — already sound, no reason to reinvent it.

Radius — **increased** for a softer, more premium feel:

| Token | Old | New |
|---|---|---|
| `--hms-radius-sm` | 4px | **6px** |
| `--hms-radius-md` | 8px | **10px** |
| `--hms-radius-lg` | 12px | **16px** |
| `--hms-radius-xl` | *(new)* | **20px** |
| `--hms-radius-full` | 999px | 999px |

### 2.4 Elevation

```
--hms-shadow-sm: 0 1px 2px rgba(15,23,42,.04), 0 1px 3px rgba(15,23,42,.06);
--hms-shadow-md: 0 4px 6px rgba(15,23,42,.05), 0 10px 15px -3px rgba(15,23,42,.08);
--hms-shadow-lg: 0 10px 15px rgba(15,23,42,.06), 0 20px 25px -5px rgba(15,23,42,.10);
--hms-shadow-focus: 0 0 0 4px rgba(79,70,229,.15);  /* indigo focus glow, paired with a real
   2px solid outline underneath for actual accessibility compliance — never glow-only */
```

### 2.5 Icons — Lucide

Replace the Google Material Icons ligature font app-wide with **`lucide-angular`** (official Angular icon component package, install via `npm install lucide-angular`): thin, consistent 1.5–2px stroke, 24×24 grid, tree-shakeable.

Phased with the rest of the rollout (Lab first, as part of its own redesign pass) — not one giant cross-cutting sweep, so no module regresses to a half-Material/half-Lucide look mid-transition.

Mapping table (representative — same approach applies to icons encountered in later modules):

| Current (Material) | New (Lucide) | Used for |
|---|---|---|
| `biotech` | `FlaskConical` | Lab module nav icon |
| `science` | `TestTube2` | Lab Component master |
| `assignment_add` | `ClipboardPlus` | Lab Requisition |
| `point_of_sale` | `CreditCard` | Lab & X-Ray/Scan Billing |
| `fact_check` | `ClipboardCheck` | Entry Queue & Report |
| `drafts` | `FileEdit` | Draft Report |
| `verified` | `BadgeCheck` | Approved Report |
| `category` | `Tags` | Lab Category master |
| `list_alt` | `ListTree` | Lab Sub-Category master |
| `summarize` | `FileBarChart` | Summary Collection Report |
| `receipt_long` | `Receipt` | Detail/collection reports |
| `currency_exchange` | `Banknote` | Payment Refund |
| `assignment_return` | `Undo2` | Refund Report |
| `event_busy` | `CalendarX` | Cancelled Report |
| `search` | `Search` | Search boxes (shared) |
| `search_off` | `SearchX` | Empty state (shared) |
| `person_search` | `UserSearch` | Patient lookup (shared) |
| `check_circle` / `task_alt` | `CheckCircle2` | Success/complete (shared) |
| `cancel` | `XCircle` | Error (shared) |
| `warning` | `AlertTriangle` | Warning (shared) |
| `pencil` (edit action) | `Pencil` | Row edit buttons |
| `delete` (delete action) | `Trash2` | Row delete buttons |
| `add`/`plus` | `Plus` | Add-new actions |
| `filter_alt` | `Filter` | Filter/apply actions |
| `download` | `Download` | Export actions |
| `print` | `Printer` | Print actions |
| `arrow_back` | `ArrowLeft` | Back navigation |

### 2.6 Motion

The current app has essentially no motion. All animation is gated behind `@media (prefers-reduced-motion: no-preference)` with an instant/no-op fallback — non-negotiable for accessibility.

```
--hms-duration-instant: 100ms;
--hms-duration-fast: 150ms;
--hms-duration-base: 220ms;
--hms-duration-slow: 350ms;
--hms-easing-standard: cubic-bezier(0.4, 0, 0.2, 1);
--hms-easing-emphasized: cubic-bezier(0.2, 0, 0, 1);
--hms-easing-exit: cubic-bezier(0.4, 0, 1, 1);
```

Concrete patterns (CSS-only or Angular's built-in `@angular/animations` — no new animation library dependency):
- **Card/row hover**: `translateY(-2px)` + shadow-sm→shadow-md, `--hms-duration-fast`.
- **Button press**: `scale(0.98)`, `--hms-duration-instant`.
- **Route/page transition**: fade + 8px slide-up on entering view, `--hms-duration-base`, via Angular Router's `animate` transitions.
- **Skeleton loading**: gradient-sweep shimmer replacing bare spinners on table/card first-load, `1.4s` infinite.
- **Stat-tile number count-up**: numbers animate from 0 on first render (instant if reduced-motion).
- **List/table row stagger**: 20–30ms incremental delay per row on first render, capped at the first ~10 rows.
- **Toast**: slide-in from top-right with a slight overshoot/settle, matching `ToastStackComponent`'s existing timing hooks (presentation-only change, API/service surface untouched).

### 2.7 Accessibility

- WCAG 2.1 AA minimum: 4.5:1 text contrast, 3:1 for large text/UI component boundaries.
- Every interactive element keeps a visible `:focus-visible` ring (2px solid `--hms-color-primary` + `--hms-shadow-focus` glow as polish, never glow-only).
- **No changes to keyboard navigation, tab order, or existing keyboard event handlers** — functional, not presentational, explicitly out of scope. Only the *visual* focus treatment changes.
- All existing `aria-label`/`aria-hidden` attributes preserved exactly; icon-swap (Material→Lucide) does not touch accessibility attributes.
- Color is never the sole status indicator — `status-badge` already pairs color with a text label; extend this convention everywhere statuses appear.
- Minimum touch target 44×44px on mobile for all interactive controls.

### 2.8 Responsive Rules

```
$hms-bp-mobile: 599px;
$hms-bp-tablet: 959px;
$hms-bp-desktop: 1280px;
$hms-bp-wide: 1440px;   /* new — matches --hms-content-max-width */
```

- **Table-heavy screens**: keep the existing horizontal-scroll-in-card convention, plus a premium addition: `position: sticky` first column + sticky header row for long tables (pure CSS, zero functional change).
- **Card-grid screens**: keep `repeat(auto-fit/auto-fill, minmax(Npx, 1fr))` — already the app's best-practice pattern, no breakpoint math needed.
- **Dense forms** (e.g., Lab Component's 13-field form): collapse from the current flat `hms-form-grid` auto-fit into logical fieldset groupings that stack cleanly on mobile — detailed in §3.

---

## 3. Flagship Module: Lab & Investigation — Full Redesign

Covering all 27 screens via their 5 logical groups. Every change below is presentation/template/SCSS only — see §6 for explicit functional-parity guardrails.

### 3.1 Masters (Lab Category, Sub-Category, Component)

**Before**: orange `#e65100` banner-header form above a dense table; Component master has 13 flat fields in one grid; delete uses the shared `confirm-dialog` (kept) but list rows show no visual distinction beyond a background-color hack for "editing."

**After**:
- Replace the orange banner with `<app-section-card icon="Plus" tone="primary" title="Add Lab Category">` (or Sub-Category/Component) — matches the pattern already proven in Clinic Settings Appearance.
- Component master's 13 fields regroup into 3 `section-card`-nested fieldsets: **Identity** (category/sub-category/name/heading/ordering), **Ranges** (male/female from-to, normal range text), **Measurement** (units, conventional factor, SI unit, sample type, method, field type) — same fields, same `formControlName`/`[(ngModel)]` bindings, just visually grouped.
- Table wrapper unchanged (`.hms-table`, already on the standard Data Grid style per commit `328c96f`) — add sticky header per §2.8, replace plain Edit/Delete icon-buttons with Lucide `Pencil`/`Trash2` in `mat-icon-button`s (same click handlers).
- "Editing" row state: replace the ad-hoc background hack with the new primary-container tint + left border accent.
- Toolbar ("Show N entries" + search): restyle using the existing shared `app-table-search` component instead of a bespoke search box.

### 3.2 Requisition / Billing (Patient Search → Requisition Form → Billing → Invoice → Receipt)

**Before**: patient-search results in a plain table; requisition form is a long vertical list of checkbox groups with no visual hierarchy; investigation billing is a manual line-item builder in a flat form; billing worklist has zero filters on a 10-column table.

**After**:
- *Patient Search*: results become a card-row pattern (mirrors Book Appointment's doctor-card treatment) instead of a plain table.
- *Requisition Form*: the checkbox-per-test list gets category-grouped `section-card`s instead of an undifferentiated vertical stack — same checkboxes, same bindings, visually chunked.
- *Investigation Billing*: the line-item entry row becomes a `section-card` with the running total pinned in a `stat-tile`-style summary strip above the line-items table.
- *Billing Worklist*: add a filter bar (date range + status) — currently has none; add `app-status-badge` for the requisition status column.
- *Invoice Billing* / *Receipt*: invoice-billing form gets the same card/spacing treatment; Receipt gets the same careful print-scope treatment as Test Report Print (§3.3) — typography/token/badge updates only, letterhead and layout geometry preserved.

### 3.3 Test Entry (Entry Queue, Approved Reports, Result Entry, Print)

**Before**: entry queue is a bare filtered table with plain-text status; the result-entry screen is the densest in the module — a grouped results table with inline inputs and a green banner for specimen types.

**After**:
- *Entry Queue* / *Draft Report* / *Approved Reports*: add `app-status-badge` (New→info, Draft→warning, Approved→success) replacing plain text; add a small `stat-tile` row above each queue using data already available from the existing list response.
- *Result Entry*: specimen-type green banner becomes a `section-card tone="tertiary"` chip row (teal) instead of a hardcoded green bar; grouped results table keeps its exact structure but abnormal values get `status-badge tone="danger"` treatment, normal-range text de-emphasized so abnormal values pop first. Irreversible-approval warning becomes a proper `tone="warning"` inline alert.
- *Test Report Print*: **included in scope, treated carefully**. Letterhead, signature block, and result-table structure stay pixel-stable; typography moves to Inter, group-header rows pick up the new tint instead of raw hex, status/abnormal flagging matches the on-screen entry form. Inline print CSS (currently a template string in the `.ts` file) gets extracted to a proper `@media print` block in `.scss` — packaging cleanup, not a visual/functional change.

### 3.4 Reports (Summary / Lab Detail / Investigation Detail / Cancelled Collection Reports)

**Before**: wide tables (11–13 columns) with a `<tfoot>` totals row and a separate small recap box — no at-a-glance summary.

**After**: add a `stat-tile` row above each report (Total Invoice Amount, Total Collected, Total Refunded, Record Count) sourced from the exact same `report().totals` object already bound to the `<tfoot>` — pure presentation of existing data, not a new computation. Detail table stays as-is below (sticky header per §2.8). Filters get the `section-card` treatment matching Reception's filter bar.

### 3.5 Refunds (Payment Refund, Refund Receipt, Refund Report)

**Before**: invoice search → single candidate row → conditional form; refund report is a 13-column table with a totals footer, same pattern as §3.4.

**After**: candidate row becomes a `section-card` summary with the refund form as a natural continuation below; "already refunded" message becomes a proper `tone="neutral"` inline state. Refund Report gets the same stat-tile treatment as §3.4. Refund Receipt gets the same careful print treatment as §3.3.

---

## 4. Before vs. After — Summary Comparison

| Aspect | Before | After |
|---|---|---|
| Header banners | Hardcoded `#e65100` orange, 11+ screens | `app-section-card` with Meridian indigo/teal tones |
| Status display | Plain text (`'New'`, `'Draft'`) | `app-status-badge` with tone-mapped color + label |
| Icons | Material Icons ligature font | Lucide, thin-stroke, consistent 24px grid |
| Report totals | Buried in `<tfoot>`, must scroll wide table | `stat-tile` summary row above the table, same source data |
| Dense forms | Flat 13-field grid (Component master) | Grouped fieldsets (Identity / Ranges / Measurement) |
| Motion | None | Hover lift, skeleton loading, staggered row entrance, reduced-motion respected |
| Typography | Poppins | Inter (UI/headings) + IBM Plex Mono (numeric data) |
| Filters | Missing entirely on some screens (Billing Worklist) | Consistent filter-bar pattern app-wide |
| Long tables on mobile | Horizontal scroll, no sticky header | Horizontal scroll + sticky header/first-column |

## 5. Remaining 14 Modules — Confidence-Rated Audit

| Module | Status | Confidence | Notes |
|---|---|---|---|
| doctor-queue | Already modern | Confirmed | Reference pattern for this whole redesign |
| appointments | Already modern | Confirmed | Booking wizard, reception worklist both strong |
| masters-admin (incl. Clinic Settings) | Already modern | Confirmed | Best selected-state precedent (preset gallery) |
| direct-billing | Already modern | Confirmed (git log) | |
| lab | Dated → this doc | Confirmed | Flagship, §3 above |
| icd | Dated | Confirmed (`#e65100` leftover found) | Same treatment as Lab, not yet detailed |
| ceo-dashboard | Likely already modern | Indirect (`stat-tile` fix commit) | Needs a short confirm pass |
| reports-mis | Likely already modern | Indirect (same commit) | Needs a short confirm pass |
| cashier | Not yet audited | None | |
| insurance | Not yet audited | None | |
| ip-admission | Not yet audited | None | |
| patient-reports | Probably dated | Weak (structurally similar to Lab) | |
| pharmacy | Not yet audited | None | |
| registration | Not yet audited | None | |
| auth | Not yet audited | None | Likely small (login screen) |

**Phasing recommendation**: Lab (this doc) → ICD (same treatment, small extra audit) → patient-reports (same treatment, small extra audit) → confirm ceo-dashboard/reports-mis (quick pass) → audit-then-redesign the 6 untouched modules in priority order once Lab's approach is validated. Each module gets its own short design addendum and its own approval round before implementation.

## 6. Impact Analysis — Zero Functional Change Guardrails

- **No `.ts` logic changes**: no new/removed component methods, no changed signal/service logic, no changed API calls, no changed route definitions, guards, or validators.
- **Template changes are structural-only**: wrapping existing elements in new container markup, swapping a raw `<button>` for a styled `mat-flat-button`/Lucide-icon button, replacing inline status text with `<app-status-badge>` — always preserving the exact existing `(click)`, `[disabled]`, `*ngIf`/`@if`, `formControlName`, `[(ngModel)]`, and interpolation bindings verbatim.
- **Icon swap is presentation-only**: `<mat-icon>` → `<lucide-icon>` changes the rendered element, never the surrounding click handler or `aria-label`.
- **Toast migration — confirmed in scope, inline with each redesign**: replacing an ad-hoc inline success/error message `<div>` with the existing `NotificationService` is a one-line `.ts` addition (an injected service call) — the one intentional exception to "template/CSS-only," matching this repo's own precedent. Call it out explicitly in the implementation commit message every time.
- **Print layouts — confirmed in scope** (Test Report Print, Lab Receipt, Refund Receipt): typography, token colors, and badge treatment update; physical layout geometry (letterhead position, margins, page breaks, signature block placement) stays pixel-stable.
- **Verification method**: `hms-web` already has a Playwright e2e suite. Run it after each module's redesign commit — a failing e2e test after a "presentation-only" change is the signal something crossed into functional territory.

## 7. Status

Design document and 3-screen live prototype (Component Master with Before/After toggle, Test Entry, Collection Report) both approved. Implementation has not started. See the companion implementation prompt for how to kick off Phase 1 (Masters) in a fresh session.
