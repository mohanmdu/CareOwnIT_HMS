package com.pms.settings.entity;

/** Maps to the --hms-transition-fast/--hms-transition-base durations - not a free numeric input. NONE sets both to 0ms, effectively disabling every CSS transition that references them. */
public enum AnimationSpeed {
    NONE,
    SUBTLE,
    STANDARD
}
