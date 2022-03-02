/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.opentelemetry.context;

import hudson.scm.SCM;
import hudson.tasks.BuildStep;
import io.opentelemetry.context.ContextKey;

import javax.annotation.concurrent.Immutable;

/**
 * See {@code io.opentelemetry.api.trace.SpanContextKey}
 */
@Immutable
public final class ScmContextKey {
    public static final ContextKey<SCM> KEY = ContextKey.named(ScmContextKey.class.getName());

    private ScmContextKey(){}
}
