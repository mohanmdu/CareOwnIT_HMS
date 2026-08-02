package com.pms.superadmin.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;

// licensedModules is the kebab-case ModuleKey.key() vocabulary, same
// wire-format convention as RoleDto.permittedModules. Read-only on
// create/update - the license itself is only ever changed via
// ClientController's dedicated /modules endpoint (see ClientService.
// updateModules()), never as a side effect of editing name/code.
public record ClientDto(
        Long id,
        @NotBlank String name,
        @NotBlank String code,
        String status,
        List<String> licensedModules,
        Instant createdAt) {
}
