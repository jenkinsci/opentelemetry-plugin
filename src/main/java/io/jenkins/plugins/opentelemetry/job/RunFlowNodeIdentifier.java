/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import edu.umd.cs.findbugs.annotations.NonNull;
import net.jcip.annotations.Immutable;
import java.util.Objects;

@Immutable
public class RunFlowNodeIdentifier extends RunIdentifier {
    final String flowNodeId;

    public RunFlowNodeIdentifier(@NonNull String jobFullName, @NonNull int runNumber, @NonNull String flowNodeId) {
        super(jobFullName, runNumber);
        this.flowNodeId = flowNodeId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        RunFlowNodeIdentifier that = (RunFlowNodeIdentifier) o;
        return flowNodeId.equals(that.flowNodeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), flowNodeId);
    }

    @Override
    public String toString() {
        return "RunFlowNodeIdentifier{" +
            "jobName='" + jobName + '\'' +
            ", runNumber=" + runNumber +
            ", flowNodeId='" + flowNodeId + '\'' +
            '}';
    }
}
