/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.queue;

import hudson.model.InvisibleAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Tracks queue state phase transitions for a build.
 *
 * Attached to a {@link hudson.model.Queue.WaitingItem} and automatically propagated to the resulting
 * {@link hudson.model.Run} by Jenkins' executor action-copying mechanism, so it is available in
 * {@code MonitoringRunListener._onInitialize()} via {@code run.getAction(QueueItemMonitoringAction.class)}.
 *
 * Jenkins copies action references (not deep copies) when items transition between queue states
 * (WaitingItem → BlockedItem → BuildableItem), so the same instance is shared across all state objects.
 *
 * <p>Note: {@link #endCurrentPhase()} is guaranteed to be called before
 * {@code _onInitialize} reads the phases because {@code Queue.maintain()} calls
 * {@code onLeaveBuildable} while holding the Queue lock, before releasing it to let the executor
 * thread proceed.
 */
public class QueueItemMonitoringAction extends InvisibleAction {

    private final List<QueuePhaseRecord> phases = new ArrayList<>();
    private String currentPhaseName;
    private String currentPhaseReason;
    private String currentPhaseLabel;
    private long currentPhaseStartMillis;

    public synchronized void startPhase(String name, String reason) {
        startPhase(name, reason, null);
    }

    public synchronized void startPhase(String name, String reason, String label) {
        // Close any phase that wasn't properly ended (e.g. unexpected state transitions
        // caused by third-party plugins calling Queue.cancel() outside the normal callbacks)
        if (currentPhaseName != null) {
            endCurrentPhase();
        }
        currentPhaseName = name;
        currentPhaseReason = reason;
        currentPhaseLabel = label;
        currentPhaseStartMillis = System.currentTimeMillis();
    }

    public synchronized void endCurrentPhase() {
        if (currentPhaseName == null) {
            return;
        }
        long endMillis = System.currentTimeMillis();
        // Collapse repeated consecutive phases with the same name/reason/label into one span.
        // Jenkins calls onEnterBlocked/onLeaveBlocked on every Queue.maintain() tick, so the
        // same blockage reason can produce hundreds of records without this guard.
        if (!phases.isEmpty()) {
            var last = phases.get(phases.size() - 1);
            if (Objects.equals(last.getPhaseName(), currentPhaseName)
                    && Objects.equals(last.getReason(), currentPhaseReason)
                    && Objects.equals(last.getLabel(), currentPhaseLabel)) {
                last.extendEnd(endMillis);
                currentPhaseName = null;
                currentPhaseReason = null;
                currentPhaseLabel = null;
                currentPhaseStartMillis = 0;
                return;
            }
        }
        phases.add(new QueuePhaseRecord(
                currentPhaseName, currentPhaseReason, currentPhaseLabel, currentPhaseStartMillis, endMillis));
        currentPhaseName = null;
        currentPhaseReason = null;
        currentPhaseLabel = null;
        currentPhaseStartMillis = 0;
    }

    /** Returns completed phases only; any currently open phase is not included. */
    public synchronized List<QueuePhaseRecord> getPhases() {
        return Collections.unmodifiableList(phases);
    }
}
