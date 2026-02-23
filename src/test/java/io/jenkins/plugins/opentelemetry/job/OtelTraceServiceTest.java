/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hudson.model.AbstractBuild;
import hudson.tasks.BuildStep;
import io.jenkins.plugins.opentelemetry.job.action.BuildStepMonitoringAction;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OtelTraceServiceTest {

    @AfterEach
    void restoreStrictMode() {
        OtelTraceService.STRICT_MODE = false;
    }

    @Test
    void removeBuildStepSpan_missingValidSpan_doesNotThrowWhenStrictModeIsDisabled() {
        OtelTraceService service = new OtelTraceService();
        AbstractBuild<?, ?> build = mock(AbstractBuild.class);
        BuildStep buildStep = mock(BuildStep.class);
        when(build.getActions(BuildStepMonitoringAction.class)).thenReturn(Collections.emptyList());
        Span validSpan = Span.wrap(SpanContext.create(
                "0123456789abcdef0123456789abcdef",
                "0123456789abcdef",
                TraceFlags.getSampled(),
                TraceState.getDefault()));

        OtelTraceService.STRICT_MODE = false;

        assertDoesNotThrow(() -> service.removeBuildStepSpan(build, buildStep, validSpan));
    }

    @Test
    void removeBuildStepSpan_missingValidSpan_throwsWhenStrictModeIsEnabled() {
        OtelTraceService service = new OtelTraceService();
        AbstractBuild<?, ?> build = mock(AbstractBuild.class);
        BuildStep buildStep = mock(BuildStep.class);
        when(build.getActions(BuildStepMonitoringAction.class)).thenReturn(Collections.emptyList());
        Span validSpan = Span.wrap(SpanContext.create(
                "0123456789abcdef0123456789abcdef",
                "0123456789abcdef",
                TraceFlags.getSampled(),
                TraceState.getDefault()));

        OtelTraceService.STRICT_MODE = true;

        assertThrows(IllegalStateException.class, () -> service.removeBuildStepSpan(build, buildStep, validSpan));
    }

    @Test
    void removeBuildStepSpan_missingInvalidSpan_doesNotThrowEvenWhenStrictModeIsEnabled() {
        OtelTraceService service = new OtelTraceService();
        AbstractBuild<?, ?> build = mock(AbstractBuild.class);
        BuildStep buildStep = mock(BuildStep.class);
        when(build.getActions(BuildStepMonitoringAction.class)).thenReturn(Collections.emptyList());
        Span invalidSpan = Span.wrap(SpanContext.create(
                "00000000000000000000000000000000",
                "0000000000000000",
                TraceFlags.getDefault(),
                TraceState.getDefault()));

        assertFalse(invalidSpan.getSpanContext().isValid());
        OtelTraceService.STRICT_MODE = true;

        assertDoesNotThrow(() -> service.removeBuildStepSpan(build, buildStep, invalidSpan));
    }
}
