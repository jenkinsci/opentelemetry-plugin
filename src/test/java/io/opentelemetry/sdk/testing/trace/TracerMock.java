/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.testing.trace;

import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;

public class TracerMock implements Tracer {
    @Override
    public SpanBuilder spanBuilder(String spanName) {
        return new SpanBuilderMock(spanName);
    }
}