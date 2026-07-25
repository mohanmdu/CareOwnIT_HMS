package com.pms.website.dto;

/**
 * Deliberately minimal - "knows the mobile number" is a weak proof of
 * identity, so a lookup response never echoes back mobile/email/address/DOB
 * to an anonymous caller. Just enough for a "Is this you?" pick-list.
 */
public record PatientLookupResult(Long patientId, String name, Integer age, String gender) {
}
