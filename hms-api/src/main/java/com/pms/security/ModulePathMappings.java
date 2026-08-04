package com.pms.security;

import com.pms.masters.entity.ModuleKey;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The canonical {apiPrefix -> ModuleKey} table - the single place mapping
 * every @RequestMapping in the backend to the sidenav module that owns it
 * (matches hms-web/src/app/layout/nav-config.ts's route groupings; keep
 * these in sync the same way ModuleKey itself must stay in sync with its
 * frontend TS union). Checked via longest-prefix match so a more specific
 * path (e.g. an activity-log sub-resource) can override its parent's
 * module if it's ever added.
 *
 * Anything under PUBLIC_PREFIXES is unauthenticated by design (the public
 * hospital website's API surface, health/info actuator endpoints, the
 * login endpoint itself, and uploaded static files) - never gate these
 * behind a module.
 *
 * /uploads/ specifically: only the PUBLIC half of uploaded files now lives
 * here (consultant photos, CMS images, clinic branding - see
 * FileStorageService) - genuinely meant to be visible to anyone with the
 * URL, referenced via plain <img src=...> in both hms-web and the public
 * hms-website, where browsers never attach an Authorization header.
 * PHI/PHI-adjacent uploads (patient photos, admission photos, patient
 * report documents) are deliberately NOT under this prefix - see
 * PrivateFileController and its /api/files/* entries below, which require
 * the same authentication and per-module gating as everything else.
 *
 * /error is here for a different reason - it's Spring Boot's own internal
 * error-rendering dispatch (BasicErrorController), reached via an internal
 * forward whenever a request fails in a way that doesn't produce a real
 * exception this app's own GlobalExceptionHandler can catch - most notably,
 * a missing static resource under /uploads/** (e.g. a stale/old-format
 * link). Without this entry, ModuleAuthorizationManager evaluates that
 * forwarded /error request against an anonymous authentication (the
 * original request's identity doesn't carry over) and denies it, turning
 * an accurate 404 into a misleading 401 "Authentication required" -
 * exactly the failure mode GlobalExceptionHandler's own MethodArgumentNotValidException
 * handler was added to prevent for actual exceptions, generalized here to
 * cover /error dispatches that were never a catchable exception at all.
 * Public here doesn't leak anything - /error only ever renders whatever
 * status/message the ORIGINAL failed request already revealed.
 */
@Component
public class ModulePathMappings {

    public static final Set<String> PUBLIC_PREFIXES = Set.of(
            "/api/public/",
            "/api/auth/login",
            "/api/super-admin/auth/login",
            "/actuator/health",
            "/actuator/info",
            "/uploads/",
            "/error");

    private static final Map<String, ModuleKey> PREFIX_TO_MODULE = buildTable();

    private static Map<String, ModuleKey> buildTable() {
        Map<String, ModuleKey> map = new LinkedHashMap<>();

        // Overview
        map.put("/api/reports/ceo-dashboard", ModuleKey.CEO_DASHBOARD);
        map.put("/api/reports", ModuleKey.OVERVIEW);
        map.put("/api/cashier/", ModuleKey.CASHIER);

        // Patient Registration
        map.put("/api/registration/patients", ModuleKey.PATIENT_REGISTRATION);

        // Appointments / Direct Visit
        map.put("/api/registration/appointments", ModuleKey.APPOINTMENTS);
        map.put("/api/registration/op-direct-billing", ModuleKey.APPOINTMENTS);
        map.put("/api/registration/refunds", ModuleKey.APPOINTMENTS);
        map.put("/api/registration/op-case-sheets", ModuleKey.APPOINTMENTS);
        map.put("/api/registration/doctor-queue", ModuleKey.APPOINTMENTS);

        // IP Admission Module
        map.put("/api/ip/admissions", ModuleKey.IP_ADMISSION);

        // Discharge Summary Module
        map.put("/api/discharge-summary", ModuleKey.DISCHARGE_SUMMARY);

        // Room / Ward Management
        map.put("/api/ip/room-types", ModuleKey.ROOM_WARD);
        map.put("/api/ip/rooms", ModuleKey.ROOM_WARD);

        // Lab & Investigations Module
        map.put("/api/lab/", ModuleKey.LAB);

        // ICD Codes Module
        map.put("/api/icd", ModuleKey.ICD_CODES);

        // Upload Reports
        map.put("/api/registration/patient-reports", ModuleKey.UPLOAD_REPORTS);

        // Pharmacy
        map.put("/api/pharmacy/", ModuleKey.PHARMACY);

        // Insurance Module
        map.put("/api/insurance/", ModuleKey.INSURANCE);

        // IP Billing Master (includes the IP Billing ledger/line-items/payments workspace)
        map.put("/api/masters/ip-billing-components/activity-log", ModuleKey.IP_BILLING_MASTER);
        map.put("/api/masters/ip-billing-categories", ModuleKey.IP_BILLING_MASTER);
        map.put("/api/masters/ip-billing-components", ModuleKey.IP_BILLING_MASTER);
        map.put("/api/ipbilling", ModuleKey.IP_BILLING_MASTER);

        // Masters
        map.put("/api/masters/departments", ModuleKey.MASTERS);
        map.put("/api/masters/consultants", ModuleKey.MASTERS);
        map.put("/api/masters/specializations", ModuleKey.MASTERS);
        map.put("/api/masters/op-billing-categories", ModuleKey.MASTERS);
        map.put("/api/masters/op-billing-components", ModuleKey.MASTERS);

        // Administration
        map.put("/api/masters/roles", ModuleKey.ADMINISTRATION);
        map.put("/api/masters/general-users", ModuleKey.ADMINISTRATION);
        map.put("/api/settings/clinic", ModuleKey.ADMINISTRATION);
        map.put("/api/activity-log", ModuleKey.ADMINISTRATION);

        // Website CMS (admin side - public side is under PUBLIC_PREFIXES)
        map.put("/api/masters/cms/", ModuleKey.WEBSITE_CMS);

        // Private uploaded files (see PrivateFileController/FileStorageService.PRIVATE_CATEGORIES) -
        // gated by the same module that owns each category's upload feature.
        map.put("/api/files/patient-reports", ModuleKey.UPLOAD_REPORTS);
        map.put("/api/files/patients", ModuleKey.PATIENT_REGISTRATION);
        map.put("/api/files/admissions", ModuleKey.IP_ADMISSION);

        return map;
    }

    /** Longest matching prefix wins; null if no module owns this path (caller should treat as "not mapped"). */
    public ModuleKey resolve(String path) {
        return PREFIX_TO_MODULE.entrySet().stream()
                .filter(entry -> path.startsWith(entry.getKey()))
                .max((a, b) -> Integer.compare(a.getKey().length(), b.getKey().length()))
                .map(Map.Entry::getValue)
                .orElse(null);
    }

    public List<String> mappedPrefixes() {
        return List.copyOf(PREFIX_TO_MODULE.keySet());
    }
}
