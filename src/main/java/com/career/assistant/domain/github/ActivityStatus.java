package com.career.assistant.domain.github;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ActivityStatus {
    ACTIVE(7),
    WARNING(14),
    DORMANT(Integer.MAX_VALUE);

    private final int maxGapDays;

    public static ActivityStatus from(int gapDays) {
        if (gapDays <= ACTIVE.maxGapDays) return ACTIVE;
        if (gapDays <= WARNING.maxGapDays) return WARNING;
        return DORMANT;
    }
}
