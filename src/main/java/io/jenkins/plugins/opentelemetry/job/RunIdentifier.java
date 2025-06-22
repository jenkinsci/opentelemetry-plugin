/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import com.google.common.collect.ComparisonChain;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import java.util.Objects;
import net.jcip.annotations.Immutable;

@Immutable
public class RunIdentifier implements Comparable<RunIdentifier> {
    final String jobName;
    final int runNumber;

    static RunIdentifier fromRun(@NonNull Run run) {
        return new RunIdentifier(run.getParent().getFullName(), run.getNumber());
    }

    static RunIdentifier fromBuild(@NonNull AbstractBuild build) {
        return new RunIdentifier(build.getParent().getFullName(), build.getNumber());
    }

    public RunIdentifier(@NonNull String jobName, @NonNull int runNumber) {
        this.jobName = jobName;
        this.runNumber = runNumber;
    }

    /**
     * String identifier for this run
     */
    @NonNull
    public String getId() {
        return jobName + "#" + runNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RunIdentifier that = (RunIdentifier) o;
        return runNumber == that.runNumber && jobName.equals(that.jobName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jobName, runNumber);
    }

    @Override
    public String toString() {
        return "RunIdentifier{" + "jobName='" + jobName + '\'' + ", runNumber=" + runNumber + '}';
    }

    public String getJobName() {
        return jobName;
    }

    public int getRunNumber() {
        return runNumber;
    }

    @Override
    public int compareTo(RunIdentifier o) {
        return ComparisonChain.start()
                .compare(this.jobName, o.jobName)
                .compare(this.runNumber, o.runNumber)
                .result();
    }
}
