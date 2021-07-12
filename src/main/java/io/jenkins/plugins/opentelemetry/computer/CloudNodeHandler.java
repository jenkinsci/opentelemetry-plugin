/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.computer;

import hudson.model.Node;
import io.opentelemetry.api.trace.Span;

import javax.annotation.Nonnull;

public interface CloudNodeHandler {

    boolean canAddAttributes(@Nonnull Node node);

    void addCloudSpanAttributes(@Nonnull Node node, @Nonnull Span rootSpanBuilder) throws Exception;
}