/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.opentelemetry.context;

import hudson.model.Run;
import io.opentelemetry.context.ContextKey;

import javax.annotation.concurrent.Immutable;

/**
 * See {@code io.opentelemetry.api.trace.SpanContextKey}
 */
@Immutable
public final class RunContextKey {
    public static final ContextKey<Run> KEY = ContextKey.named(Run.class.getName());

    private RunContextKey(){}
}
