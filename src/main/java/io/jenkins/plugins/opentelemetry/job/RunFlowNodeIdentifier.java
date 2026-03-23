/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import net.jcip.annotations.Immutable;

@Immutable
/**
 * Immutable identifier composed of job name, run number, and optional pipeline flow-node id.
 */
public class RunFlowNodeIdentifier extends RunIdentifier {
    final String flowNodeId;

    /**
     * Creates a run/flow-node identifier.
     *
     * @param jobFullName full Jenkins job name
     * @param runNumber Jenkins run number
     * @param flowNodeId optional pipeline flow-node identifier
     */
    public RunFlowNodeIdentifier(@NonNull String jobFullName, int runNumber, @Nullable String flowNodeId) {
        super(jobFullName, runNumber);
        this.flowNodeId = flowNodeId;
    }

    /**
     * Compares this identifier with another run/flow-node identifier.
     *
     * @param o object to compare
     * @return {@code true} when all identifier components match
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        RunFlowNodeIdentifier that = (RunFlowNodeIdentifier) o;
        return Objects.equals(flowNodeId, that.flowNodeId);
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}.
     *
     * @return identifier hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), flowNodeId);
    }

    /**
     * Returns a debug-friendly representation of this identifier.
     *
     * @return string representation
     */
    @Override
    public String toString() {
        return "RunFlowNodeIdentifier{" + "jobName='"
                + jobName + '\'' + ", runNumber="
                + runNumber + ", flowNodeId='"
                + flowNodeId + '\'' + '}';
    }
}
