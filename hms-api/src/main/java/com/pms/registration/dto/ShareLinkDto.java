package com.pms.registration.dto;

/** A time-limited, unauthenticated URL for one specific patient report - see JwtService.issueReportShareToken() and PublicPatientReportController. */
public record ShareLinkDto(String url) {
}
