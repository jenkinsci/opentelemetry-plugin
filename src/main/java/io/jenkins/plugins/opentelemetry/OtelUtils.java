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
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.mixin.ChangeRequestSCMHead;
import jenkins.scm.api.mixin.TagSCMHead;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Function;

public class OtelUtils {

    public static final String FREESTYLE = "freestyle";
    public static final String MULTIBRANCH = "multibranch";
    public static final String WORKFLOW = "workflow";
    public static final String UNKNOWN = "unknown";
    public static final String BRANCH = "branch";
    public static final String CHANGE_REQUEST = "change_request";
    public static final String TAG = "tag";

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
            return FREESTYLE;
        }
        if (isMultibranch(run)) {
            return MULTIBRANCH;
        }
        if (isWorkflow(run)) {
            return WORKFLOW;
        }
        // TODO: support for
        // https://github.com/jenkinsci/matrix-project-plugin/blob/master/src/main/java/hudson/matrix/MatrixBuild.java#L70
        return UNKNOWN;
    }

    @Nonnull
    public static String getMultibranchType(Run run) {
        if (isMultibranch(run)) {
            if (isMultibranchChangeRequest(run)) {
                return CHANGE_REQUEST;
            }
            if (isMultibranchBranch(run)) {
                return BRANCH;
            }
            if (isMultibranchTag(run)) {
                return TAG;
            }
        }
        return UNKNOWN;
    }

    public static boolean isMultibranchTag(Run run) {
        if (isMultibranch(run)) {
            return (SCMHead.HeadByItem.findHead(run.getParent()) instanceof TagSCMHead);
        }
        return false;
    }

    public static boolean isMultibranchChangeRequest(Run run) {
        if (isMultibranch(run)) {
            return (SCMHead.HeadByItem.findHead(run.getParent()) instanceof ChangeRequestSCMHead);
        }
        return false;
    }

    public static boolean isMultibranchBranch(Run run) {
        if (isMultibranch(run)) {
            return !(isMultibranchChangeRequest(run) || isMultibranchTag(run));
        }
        return false;
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
