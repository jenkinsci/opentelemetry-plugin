/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import hudson.model.FreeStyleBuild;
import hudson.model.Run;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Function;

public class OtelUtils {
    @Nonnull
    public static Function<Span, String> spanToDebugString() {
        return span -> {
            if (span == null) {
                return "#null#";
            } else if (span instanceof ReadableSpan) {
                ReadableSpan readableSpan = (ReadableSpan) span;
                SpanData spanData = readableSpan.toSpanData();
                return "span(" +
                        "name: " + readableSpan.getName() + ", "+
                        "spanId: " + spanData.getSpanId() + ", " +
                        "parentSpanId: " + spanData.getParentSpanId() + ", " +
                        "traceId: " + spanData.getTraceId() + ", " +
                         ")";
            } else {
                return span.toString();
            }
        };
    }

    @Nonnull
    public static String getProjectType(Run run) {
        if (isFreestyle(run)) {
            return "freestyle";
        }
        if (isMultibranch(run)) {
            return "multibranch";
        }
        if (isWorkflow(run)) {
            return "workflow";
        }
        // TODO: support for
        // https://github.com/jenkinsci/matrix-project-plugin/blob/master/src/main/java/hudson/matrix/MatrixBuild.java#L70
        return "unknown";
    }

    @Nonnull
    public static boolean isMultibranch(Run run) {
        if (run == null) {
            return false;
        }
        return (run instanceof WorkflowRun && run.getParent().getParent() instanceof WorkflowMultiBranchProject);
    }

    @Nonnull
    public static boolean isWorkflow(Run run) {
        if (run == null) {
            return false;
        }
        return (run instanceof WorkflowRun && !(run.getParent().getParent() instanceof WorkflowMultiBranchProject));
    }

    @Nonnull
    public static boolean isFreestyle(Run run) {
        if (run == null) {
            return false;
        }
        return (run instanceof FreeStyleBuild);
    }

    @Nonnull
    public static String toDebugString(@Nullable Span span) {
        return spanToDebugString().apply(span);
    }
}
