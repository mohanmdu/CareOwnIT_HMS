package com.pms.cashier.dto;

/** The Cashier Collection Report's source discriminator - one value per genuine billing/payment event type across the app. */
public enum CollectionType {
    APPOINTMENT_FEE,
    OP_DIRECT_BILLING,
    LAB_CHARGES,
    INVESTIGATION_CHARGES,
    IP_ADVANCE,
    IP_FINAL_SETTLEMENT,
    IP_DUE_COLLECTION,
    PHARMACY_BILL
}
