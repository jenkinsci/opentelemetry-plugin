/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.cause;

import hudson.model.Cause;

import javax.annotation.Nonnull;

public interface CauseHandler {

    boolean isSupported(@Nonnull Cause cause);

    boolean canAddAttributes(@Nonnull Cause cause);

    /**
     * Machine-readable description of the cause like "UserIdCause:anonymous"...
     */
    @Nonnull
    String getStructuredDescription();
}