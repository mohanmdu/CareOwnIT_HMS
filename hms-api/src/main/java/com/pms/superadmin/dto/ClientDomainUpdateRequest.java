package com.pms.superadmin.dto;

import jakarta.validation.constraints.Pattern;

/** domain is nullable/blank-able by design - clears the client's custom-domain routing rather than requiring one to always be set. */
public record ClientDomainUpdateRequest(
        @Pattern(
                        regexp = "^$|^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+$",
                        message = "must be a valid lowercase hostname (e.g. clienta-hospital.com), or blank to clear it")
                String domain) {
}
