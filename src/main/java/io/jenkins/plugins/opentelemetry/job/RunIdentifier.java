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

    /**
     * Creates a run identifier.
     *
     * @param jobName full job name
     * @param runNumber build run number
     */
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

    /**
     * Compares by job name and run number.
     *
     * @param o object to compare
     * @return {@code true} when both identify the same run
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RunIdentifier that = (RunIdentifier) o;
        return runNumber == that.runNumber && jobName.equals(that.jobName);
    }

    /**
     * Returns hash code for this run identifier.
     *
     * @return hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(jobName, runNumber);
    }

    /**
     * Returns string representation for diagnostics.
     *
     * @return printable run identifier
     */
    @Override
    public String toString() {
        return "RunIdentifier{" + "jobName='" + jobName + '\'' + ", runNumber=" + runNumber + '}';
    }

    /**
     * Returns full job name.
     *
     * @return full job name
     */
    public String getJobName() {
        return jobName;
    }

    /**
     * Returns build run number.
     *
     * @return run number
     */
    public int getRunNumber() {
        return runNumber;
    }

    /**
     * Compares run identifiers by job name then run number.
     *
     * @param o run identifier to compare
     * @return comparison result
     */
    @Override
    public int compareTo(RunIdentifier o) {
        return ComparisonChain.start()
                .compare(this.jobName, o.jobName)
                .compare(this.runNumber, o.runNumber)
                .result();
    }
}
