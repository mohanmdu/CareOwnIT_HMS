package com.pms.superadmin.dto;

/** status is one of NOT_SET / LIVE / UNREACHABLE - see ClientDomainStatusService. */
public record ClientDomainStatusResponse(String status) {
}
