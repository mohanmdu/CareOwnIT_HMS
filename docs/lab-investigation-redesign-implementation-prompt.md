# Implementation Kickoff Prompt

Paste the block below verbatim as your first message in a fresh Claude Code session, opened in this repo (`CareOwn_HMS`), to start implementation. It's self-contained — the session won't have any memory of the design/planning conversation that produced it.

---

```
Implement the Lab & Investigation module UI redesign in hms-web per the approved
design document at docs/lab-investigation-redesign.md. This is a pure visual/
presentational redesign — read §6 of that doc carefully first, since the most
important constraint is ZERO functional change: no .ts logic changes except the
one explicitly-approved exception (toast migration, §6), no changed API calls,
routes, guards, or validators, and every existing (click)/[disabled]/*ngIf/
formControlName/[(ngModel)] binding preserved verbatim.

Reference prototype (the visual target — built with system-font approximations,
load the real Inter/IBM Plex Mono/lucide-angular packages for the actual
implementation): https://claude.ai/code/artifact/387f994d-8b43-4e42-ba24-5f22ea631ca3

Work on the feature-mc-hosptial branch (create it off main if it doesn't exist
locally). Commit incrementally per screen group, not one giant commit.

Start with:
1. Global design system foundation (§2 of the doc): update
   hms-web/src/styles/_tokens.scss with the new Meridian color/radius/shadow/
   motion values, add Inter + IBM Plex Mono font loading, install
   lucide-angular (npm install lucide-angular). Don't touch any Lab screens
   yet — this step alone should change colors/fonts app-wide but nothing else,
   since class names stay the same.
2. Verify nothing broke: run the existing Playwright e2e suite (hms-web has
   one) before touching any Lab screens.
3. Then work through the Lab & Investigation module in the order given in §3
   of the doc: Masters (3.1) → Requisition/Billing (3.2) → Test Entry (3.3) →
   Reports (3.4) → Refunds (3.5). Pause after each group for my review before
   continuing to the next, rather than doing all 27 screens in one pass.

Use plan mode if you want to confirm your specific file-level approach before
touching code, but the design decisions themselves are already final — don't
re-litigate the color palette/typography/icon choices in §2, they're approved.
```

---

## If you want to hand off a *different* module later

The same design system (§2 of the main doc) applies everywhere — only §3 (the screen-by-screen redesign detail) is Lab-specific. For the next module (ICD is next in the phasing order per §5), the fastest path is:

1. Ask a fresh session to audit that module the same way Lab was audited (current screens, current SCSS patterns, hardcoded colors).
2. Write a short addendum following the same format as `docs/lab-investigation-redesign.md` §3 (Before/After per screen group) — reuse §2 (the design system) verbatim, don't redesign it again.
3. Get that addendum approved, then use the same implementation-prompt pattern above, pointed at the new addendum instead.
