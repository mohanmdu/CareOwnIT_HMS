package com.pms.masters.entity;

/**
 * One entry per NavGroup in hms-web/src/app/layout/nav-config.ts - keep these
 * in sync (mirrors that file's own "keep these in sync" comment on its
 * ModuleKey TS union). Persisted via @Enumerated(EnumType.STRING) using the
 * Java constant name (e.g. "PATIENT_REGISTRATION"), but the wire format sent
 * to/from the frontend is always the kebab-case key() string - the frontend's
 * ModuleKey union already exists with these exact kebab-case values across
 * nav-config.ts/package-config.ts, so this enum matches its wire format
 * rather than the other way around. See fromKey()/key() - deliberately plain
 * methods, not Jackson enum-serialization annotations, so the mapping stays
 * explicit and doesn't depend on Jackson 3 annotation behavior.
 */
public enum ModuleKey {
    OVERVIEW("overview"),
    PATIENT_REGISTRATION("patient-registration"),
    APPOINTMENTS("appointments"),
    BILLING("billing"),
    INSURANCE("insurance"),
    LAB("lab"),
    UPLOAD_REPORTS("upload-reports"),
    PHARMACY("pharmacy"),
    ICD_CODES("icd-codes"),
    ROOM_WARD("room-ward"),
    IP_ADMISSION("ip-admission"),
    CASHIER("cashier"),
    DISCHARGE_SUMMARY("discharge-summary"),
    IP_BILLING_MASTER("ip-billing-master"),
    WEBSITE_CMS("website-cms"),
    MASTERS("masters"),
    ADMINISTRATION("administration"),
    CEO_DASHBOARD("ceo-dashboard");

    private final String key;

    ModuleKey(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static ModuleKey fromKey(String key) {
        for (ModuleKey value : values()) {
            if (value.key.equals(key)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown module key: " + key);
    }
}
