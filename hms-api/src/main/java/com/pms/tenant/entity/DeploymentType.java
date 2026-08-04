package com.pms.tenant.entity;

/** Cosmetic/reporting metadata only - does not change how the app behaves. Both values run the identical JAR; see app.deployment.mode (DeploymentModeProperties) for the flag that actually changes login/routing behavior. */
public enum DeploymentType {
    CLOUD,
    ON_PREMISE
}
