/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.step;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.sdk.trace.ReadableSpan;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Records the OpenTelemetry context that code runs in, such as the Groovy code of a trusted pipeline script between its
 * steps: {@code io.jenkins.plugins.opentelemetry.job.step.ContextLeakProgramProbe.record(env.BUILD_NUMBER, 'id')}.
 */
public final class ContextLeakProgramProbe {
    public static final List<Observation> OBSERVATIONS = new CopyOnWriteArrayList<>();

    private ContextLeakProgramProbe() {}

    public static void record(String build, String id) {
        Span current = Span.current();
        OBSERVATIONS.add(new Observation(
                build,
                id,
                Thread.currentThread().getName(),
                current.getSpanContext(),
                current.isRecording(),
                current instanceof ReadableSpan readableSpan ? readableSpan.getName() : null));
    }

    /**
     * @param build     the number of the build that ran the code
     * @param current   the span context current when the code ran
     * @param recording whether that span was still recording (not ended)
     * @param spanName  the name of that span, if it is a span of the SDK
     */
    public record Observation(
            String build, String id, String thread, SpanContext current, boolean recording, String spanName) {}
}
