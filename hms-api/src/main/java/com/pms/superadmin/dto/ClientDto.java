package com.pms.superadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.List;

// licensedModules is the kebab-case ModuleKey.key() vocabulary, same
// wire-format convention as RoleDto.permittedModules. Read-only on
// create/update - the license itself is only ever changed via
// ClientController's dedicated /modules endpoint (see ClientService.
// updateModules()), never as a side effect of editing name/code.
//
// code's pattern is deliberately strict (not just "non-blank") - Tenant
// ProvisioningService uses it verbatim to build the schema name and MySQL
// username for this client's dedicated database (CREATE DATABASE/CREATE
// USER can't be parameterized like a normal query), so anything outside
// [A-Za-z0-9_] here would be a SQL-injection vector into DDL, not just a
// cosmetic concern.
public record ClientDto(
        Long id,
        @NotBlank String name,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]{2,40}$", message = "must be 2-40 characters: letters, numbers, underscore only") String code,
        String status,
        List<String> licensedModules,
        Instant createdAt) {
}
