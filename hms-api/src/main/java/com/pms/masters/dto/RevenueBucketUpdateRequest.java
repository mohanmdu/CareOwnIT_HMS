package com.pms.masters.dto;

import com.pms.masters.entity.RevenueBucket;
import jakarta.validation.constraints.NotNull;

/** Shared by OpBillingCategoryController and IpBillingCategoryController - same field, same enum, both /revenue-bucket endpoints. */
public record RevenueBucketUpdateRequest(@NotNull RevenueBucket revenueBucket) {
}
