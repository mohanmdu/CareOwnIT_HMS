package com.pms.superadmin.backup.dto;

import jakarta.validation.constraints.NotBlank;

/** confirmClientCode must exactly match the target client's own code - see RestoreService.restore(), the "prevent accidental restoration to the wrong client's database" safeguard. */
public record RestoreRequest(@NotBlank String confirmClientCode) {
}
