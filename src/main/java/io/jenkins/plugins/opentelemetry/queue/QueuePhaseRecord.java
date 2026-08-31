/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.queue;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Immutable record of a single completed queue state phase for a build.
 *
 * <p>Phases are appended in chronological order, so {@code phases.get(0).startMillis()} is always
 * the earliest timestamp.
 */
public class QueuePhaseRecord {

    // Non-final so XStream can populate fields via reflection
    private String phaseName;
    private String reason;
    private String label;
    private long startMillis;
    private long endMillis;

    /** Required by XStream for deserialization. */
    private QueuePhaseRecord() {}

    public QueuePhaseRecord(
            @NonNull String phaseName,
            @Nullable String reason,
            @Nullable String label,
            long startMillis,
            long endMillis) {
        this.phaseName = phaseName;
        this.reason = reason;
        this.label = label;
        this.startMillis = startMillis;
        this.endMillis = endMillis;
    }

    @NonNull
    public String getPhaseName() {
        return phaseName;
    }

    @Nullable
    public String getReason() {
        return reason;
    }

    @Nullable
    public String getLabel() {
        return label;
    }

    public long getStartMillis() {
        return startMillis;
    }

    public long getEndMillis() {
        return endMillis;
    }

    /** Extends the end timestamp when merging a repeated consecutive phase. */
    void extendEnd(long endMillis) {
        this.endMillis = endMillis;
    }
}
