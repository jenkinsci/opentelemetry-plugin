/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.cause;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Cause;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;

public interface CauseHandler extends Comparable<CauseHandler> {

    default void configure(ConfigProperties config) {}

    boolean isSupported(@NonNull Cause cause);

    /**
     * Machine-readable description of the cause like "UserIdCause:anonymous"...
     */
    @NonNull
    default String getStructuredDescription(@NonNull Cause cause) {
        return cause.getClass().getSimpleName();
    }

    /**
     * @return the ordinal of this handler to execute step handlers in predictable order. The smallest ordinal is executed first.
     */
    default int ordinal() {
        return 0;
    }

    @Override
    default int compareTo(CauseHandler other) {
        if (this.ordinal() == other.ordinal()) {
            return this.getClass().getName().compareTo(other.getClass().getName());
        } else {
            return Integer.compare(this.ordinal(), other.ordinal());
        }
    }
}
