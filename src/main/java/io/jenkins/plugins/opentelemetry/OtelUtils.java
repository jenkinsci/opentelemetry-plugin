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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.io.UnsupportedEncodingException;

public class OtelUtils {

    public static final String FREESTYLE = "freestyle";
    public static final String MATRIX = "matrix";
    public static final String MAVEN = "maven";
    public static final String MULTIBRANCH = "multibranch";
    public static final String WORKFLOW = "workflow";
    public static final String UNKNOWN = "unknown";
    public static final String BRANCH = "branch";
    public static final String CHANGE_REQUEST = "change_request";
    public static final String TAG = "tag";
    public static final String JENKINS_CORE = "jenkins-core";

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
        if (isMaven(run)) {
            return MAVEN;
        }
        if (isMatrix(run)) {
            return MATRIX;
        }
        if (isMultibranch(run)) {
            return MULTIBRANCH;
        }
        if (isWorkflow(run)) {
            return WORKFLOW;
        }
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
    public static boolean isMatrix(Run run) {
        if (run == null) {
            return false;
        }
        return isInstance(run, "hudson.matrix.MatrixBuild") ||
            isInstance(run, "hudson.matrix.MatrixProject") ||
            isInstance(run, "hudson.matrix.MatrixRun");
    }

    @Nonnull
    public static boolean isMaven(Run run) {
        if (run == null) {
            return false;
        }
        return isInstance(run, "hudson.maven.AbstractMavenBuild") ||
            isInstance(run, "hudson.maven.MavenModuleSetBuild") ||
            isInstance(run, "hudson.maven.MavenBuild");
    }

    private static boolean isInstance(Object o, String clazz) {
        if (o != null && o.getClass().getName().equals(clazz)) {
            return true;
        }
        return false;
    }

    @Nonnull
    public static String toDebugString(@Nullable Span span) {
        return spanToDebugString().apply(span);
    }

    @Nonnull
    public static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeException(ex.getCause());
        }
    }
}
