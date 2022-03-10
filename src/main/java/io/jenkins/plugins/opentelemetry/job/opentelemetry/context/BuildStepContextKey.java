/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.opentelemetry.context;

import hudson.tasks.BuildStep;
import io.opentelemetry.context.ContextKey;

import net.jcip.annotations.Immutable;

/**
 * See {@code io.opentelemetry.api.trace.SpanContextKey}
 */
@Immutable
public final class BuildStepContextKey {
    public static final ContextKey<BuildStep> KEY = ContextKey.named(BuildStepContextKey.class.getName());

    private BuildStepContextKey(){}
}
