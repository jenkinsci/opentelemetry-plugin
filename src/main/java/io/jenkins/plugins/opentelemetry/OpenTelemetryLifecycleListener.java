/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import hudson.ExtensionPoint;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;

public interface OpenTelemetryLifecycleListener extends ExtensionPoint, Comparable<OpenTelemetryLifecycleListener> {

    default void afterConfiguration(ConfigProperties configProperties){}

    /**
     * @return the ordinal of this otel component to execute step handlers in predictable order. The smallest ordinal is handled first.
     */
    default int ordinal() {
        return 0;
    }

    @Override
    default int compareTo(OpenTelemetryLifecycleListener other) {
        if (this.ordinal() == other.ordinal()) {
            return this.getClass().getName().compareTo(other.getClass().getName());
        } else {
            return Integer.compare(this.ordinal(), other.ordinal());
        }
    }
}
