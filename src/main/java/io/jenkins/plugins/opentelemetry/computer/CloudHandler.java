/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.computer;

import hudson.model.Label;
import hudson.slaves.Cloud;
import io.opentelemetry.api.trace.SpanBuilder;

import javax.annotation.Nonnull;

public interface CloudHandler {

    boolean canAddAttributes(@Nonnull Cloud cloud);

    void addCloudAttributes(@Nonnull Cloud cloud, @Nonnull Label label, @Nonnull SpanBuilder rootSpanBuilder) throws Exception;

    String getCloudName();
}