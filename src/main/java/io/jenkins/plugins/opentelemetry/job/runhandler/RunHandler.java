/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.runhandler;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Run;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;

public interface RunHandler extends Comparable<RunHandler> {

    default void configure(ConfigProperties config) {}

    boolean canCreateSpanBuilder(@NonNull Run<?, ?> run);

    @NonNull
    SpanBuilder createSpanBuilder(@NonNull Run<?, ?> run, @NonNull Tracer tracer);

    /**
     * Low cardinality pipeline name that fits with
     * {@link io.opentelemetry.semconv.incubating.CicdIncubatingAttributes#CICD_PIPELINE_NAME}.
     * High cardinality elements like the SCM pull request names of a Jenkins multibranch pipeline
     * should be excluded from the pipeline short name.
     */
    @NonNull
    String getSpanName(@NonNull Run<?, ?> run);

    /**
     * @return the ordinal of this handler to execute run handlers in predictable order. The smallest ordinal is executed first.
     */
    default int ordinal() {
        return 0;
    }

    @Override
    default int compareTo(RunHandler other) {
        if (this.ordinal() == other.ordinal()) {
            return this.getClass().getName().compareTo(other.getClass().getName());
        } else {
            return Integer.compare(this.ordinal(), other.ordinal());
        }
    }
}
