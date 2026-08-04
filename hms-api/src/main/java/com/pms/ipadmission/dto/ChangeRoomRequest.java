package com.pms.ipadmission.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record ChangeRoomRequest(@NotNull Long roomId, LocalDateTime changedAt) {
}
