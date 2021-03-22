/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import com.google.common.base.Strings;
import com.google.errorprone.annotations.MustBeClosed;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Computer;
import hudson.model.Run;
import io.jenkins.plugins.opentelemetry.OpenTelemetryAttributesAction;
import io.jenkins.plugins.opentelemetry.OtelUtils;
import io.jenkins.plugins.opentelemetry.job.jenkins.AbstractPipelineListener;
import io.jenkins.plugins.opentelemetry.job.jenkins.PipelineListener;
import io.jenkins.plugins.opentelemetry.job.opentelemetry.context.FlowNodeContextKey;
import io.jenkins.plugins.opentelemetry.job.opentelemetry.context.RunContextKey;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import org.apache.commons.compress.utils.Sets;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.StepListener;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.base.Verify.verifyNotNull;


@Extension
public class MonitoringPipelineListener extends AbstractPipelineListener implements PipelineListener, StepListener {
    private final static Logger LOGGER = Logger.getLogger(MonitoringPipelineListener.class.getName());

    private OtelTraceService otelTraceService;
    private Tracer tracer;
    private Set<String> ignoredSteps;

    @PostConstruct
    public void postConstruct() {
        // TODO make this list configurable
        this.ignoredSteps = Sets.newHashSet("dir", "echo", "isUnix", "pwd", "properties");
    }

    @Override
    public void onStartNodeStep(@Nonnull StepStartNode stepStartNode, @Nullable String nodeLabel, @Nonnull WorkflowRun run) {
        try (Scope nodeSpanScope = setupContext(run, stepStartNode)) {
            verifyNotNull(nodeSpanScope, "%s - No span found for node %s", run, stepStartNode);
            String nodeSpanName = Strings.isNullOrEmpty(nodeLabel) ? "Node" : "Node: " + nodeLabel;
            Span nodeSpan = getTracer().spanBuilder(nodeSpanName)
                    .setParent(Context.current())
                    .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_TYPE, getStepType(stepStartNode.getDescriptor(), "node"))
                    .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_ID, stepStartNode.getId())
                    .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_NAME, "node") // FIXME verify it's the right semantic and value
                    .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_NODE_LABEL, Strings.emptyToNull(nodeLabel))
                    .startSpan();
            LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - > node(" + nodeLabel + ") - begin " + OtelUtils.toDebugString(nodeSpan));

            getTracerService().putSpan(run, nodeSpan, stepStartNode);

            try (Scope allocateNodeSpanScope = nodeSpan.makeCurrent()) {
                String allocateNodeSpanName = Strings.isNullOrEmpty(nodeLabel) ? "Node Allocation" : "Node Allocation: " + nodeLabel;;
                Span allocateNodeSpan = getTracer().spanBuilder(allocateNodeSpanName)
                        .setParent(Context.current())
                        .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_TYPE, getStepType(stepStartNode.getDescriptor(), "node"))
                        .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_ID, stepStartNode.getId())
                        .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_NAME, "node.allocate") // FIXME verify it's the right semantic and value
                        .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_NODE_LABEL, Strings.emptyToNull(nodeLabel))
                        .startSpan();
                LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - > node(" + nodeLabel + ") - begin " + OtelUtils.toDebugString(allocateNodeSpan));

                getTracerService().putSpan(run, allocateNodeSpan, stepStartNode);
            }
        }
    }

    @Override
    public void onAfterStartNodeStep(@Nonnull StepStartNode stepStartNode, @Nullable String nodeLabel, @Nonnull WorkflowRun run) {
        // end the "node.allocate" span
        endCurrentSpan(stepStartNode, run);
    }

    @Override
    public void onStartStageStep(@Nonnull StepStartNode stepStartNode, @Nonnull String stageName, @Nonnull WorkflowRun run) {
        try (Scope ignored = setupContext(run, stepStartNode)) {
            verifyNotNull(ignored, "%s - No span found for node %s", run, stepStartNode);
            String spanStageName = "Stage: " + stageName;
            Span stageSpan = getTracer().spanBuilder(spanStageName)
                    .setParent(Context.current())
                    .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_TYPE, getStepType(stepStartNode.getDescriptor(), "stage"))
                    .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_ID, stepStartNode.getId())
                    .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_NAME, stageName)
                    .startSpan();
            LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - > stage(" + stageName + ") - begin " + OtelUtils.toDebugString(stageSpan));

            getTracerService().putSpan(run, stageSpan, stepStartNode);
        }
    }

    @Override
    public void onEndNodeStep(@Nonnull StepEndNode node, @Nonnull String nodeName, @Nonnull WorkflowRun run) {
        endCurrentSpan(node, run);
    }

    @Override
    public void onEndStageStep(@Nonnull StepEndNode node, @Nonnull String stageName, @Nonnull WorkflowRun run) {
        endCurrentSpan(node, run);
    }

    @Override
    public void onAtomicStep(@Nonnull StepAtomNode node, @Nonnull WorkflowRun run) {
        if (isIgnoredStep(node.getDescriptor())){
            LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - don't create span for step '" + node.getDisplayFunctionName() + "'");
            return;
        }
        try (Scope ignored = setupContext(run, node)) {
            verifyNotNull(ignored, "%s - No span found for node %s", run, node);

            String principal = Objects.toString(node.getExecution().getAuthentication().getPrincipal(), "#null#");
            LOGGER.log(Level.FINE, () -> node.getDisplayFunctionName() + " - principal: " + principal);

            SpanBuilder spanBuilder = null;
            for (StepHandler stepHandler : ExtensionList.lookup(StepHandler.class)) {
                if (stepHandler.canCreateSpanBuilder(node, run)) {
                    try {
                        spanBuilder = stepHandler.createSpanBuilder(node, run, getTracer());
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, run.getFullDisplayName() + " failure to handle step " + node + " with handler " + stepHandler, e);
                    }
                    break;
                }
            }
            if (spanBuilder == null) {
                spanBuilder = getTracer().spanBuilder(node.getDisplayFunctionName());
            }

            spanBuilder
                    .setParent(Context.current())
                    .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_TYPE, getStepType(node.getDescriptor(), "step"))
                    .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_ID, node.getId())
                    .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_NAME, getStepName(node.getDescriptor(), "step"))
                    .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_USER, principal);

            Span atomicStepSpan = spanBuilder.startSpan();
            LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - > " + node.getDisplayFunctionName() + " - begin " + OtelUtils.toDebugString(atomicStepSpan));

            getTracerService().putSpan(run, atomicStepSpan, node);
        }
    }

    @Override
    public void onAfterAtomicStep(@Nonnull StepAtomNode node, @Nonnull WorkflowRun run) {
        if (isIgnoredStep(node.getDescriptor())){
            LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - don't end span for step '" + node.getDisplayFunctionName() + "'");
            return;
        }
        endCurrentSpan(node, run);
    }

    private boolean isIgnoredStep(@Nullable StepDescriptor stepDescriptor) {
        if (stepDescriptor == null) {
            return true;
        }
        boolean ignoreStep = this.ignoredSteps.contains(stepDescriptor.getFunctionName());
        LOGGER.log(Level.FINER, ()-> "isIgnoreStep(" + stepDescriptor + "): " + ignoreStep);
        return ignoreStep;
    }

    private String getStepName(@Nullable StepDescriptor stepDescriptor, @Nonnull String name) {
        if (stepDescriptor == null) {
            return name;
        }
        return stepDescriptor.getDisplayName();
    }

    private String getStepType(@Nullable StepDescriptor stepDescriptor, @Nonnull String type) {
        if (stepDescriptor == null) {
            return type;
        }
        return stepDescriptor.getFunctionName();
    }

    @Override
    public void onStartParallelStepBranch(@Nonnull StepStartNode stepStartNode, @Nonnull String branchName, @Nonnull WorkflowRun run) {
        try (Scope ignored = setupContext(run, stepStartNode)) {
            verifyNotNull(ignored, "%s - No span found for node %s", run, stepStartNode);
            Span atomicStepSpan = getTracer().spanBuilder("Parallel branch: " + branchName)
                    .setParent(Context.current())
                    .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_TYPE, getStepType(stepStartNode.getDescriptor(), "branch"))
                    .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_ID, stepStartNode.getId())
                    .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_NAME, branchName)
                    .startSpan();
            LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - > parallel branch(" + branchName + ") - begin " + OtelUtils.toDebugString(atomicStepSpan));

            getTracerService().putSpan(run, atomicStepSpan, stepStartNode);
        }
    }

    @Override
    public void onEndParallelStepBranch(@Nonnull StepEndNode node, @Nonnull String branchName, @Nonnull WorkflowRun run) {
        endCurrentSpan(node, run);
    }

    private void endCurrentSpan(FlowNode node, WorkflowRun run) {
        try (Scope ignored = setupContext(run, node)) {
            verifyNotNull(ignored, "%s - No span found for node %s", run, node);

            Span span = getTracerService().getSpan(run, node);
            ErrorAction errorAction = node.getError();
            if (errorAction == null) {
                span.setStatus(StatusCode.OK);
            } else {
                Throwable throwable = errorAction.getError();
                span.recordException(throwable);
                span.setStatus(StatusCode.ERROR, throwable.getMessage());
            }
            span.end();
            LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - < " + node.getDisplayFunctionName() + " - end " + OtelUtils.toDebugString(span));

            getTracerService().removePipelineStepSpan(run, node, span);
        }
    }

    @Override
    public void notifyOfNewStep(@Nonnull Step step, @Nonnull StepContext context) {
        try {
            WorkflowRun run = context.get(WorkflowRun.class);
            FlowNode node = context.get(FlowNode.class);
            Computer computer = context.get(Computer.class);
            if (computer == null || node == null || run == null) {
                LOGGER.log(Level.FINER, () -> "No run, flowNode or computer, skip. Run:" + run + ", flowNode: " + node + ", computer:" + computer);
                return;
            }
            if (computer.getAction(OpenTelemetryAttributesAction.class) == null) {
                LOGGER.log(Level.WARNING, "Unexpected missing " + OpenTelemetryAttributesAction.class + " on " + computer + " fallback");
                String hostName = computer.getHostName();
                OpenTelemetryAttributesAction openTelemetryAttributesAction = new OpenTelemetryAttributesAction();
                openTelemetryAttributesAction.getAttributes().put(ResourceAttributes.HOST_NAME, hostName);
                computer.addAction(openTelemetryAttributesAction);
            }
            OpenTelemetryAttributesAction openTelemetryAttributesAction = computer.getAction(OpenTelemetryAttributesAction.class);

            try (Scope ignored = setupContext(run, node)) {
                Span currentSpan = Span.current();
                LOGGER.log(Level.FINE, () -> "Add resource attributes to span " + OtelUtils.toDebugString(currentSpan) + " - " + openTelemetryAttributesAction);
                for (Map.Entry<AttributeKey<?>, Object> entry : openTelemetryAttributesAction.getAttributes().entrySet()) {
                    AttributeKey<?> attributeKey = entry.getKey();
                    Object value = verifyNotNull(entry.getValue());
                    currentSpan.setAttribute((AttributeKey<? super Object>) attributeKey, value);
                }
            }
        } catch (IOException | InterruptedException | RuntimeException e) {
            LOGGER.log(Level.WARNING,"Exception processing " + step + " - " + context, e);
        }
    }

    /**
     * @return {@code null} if no {@link Span} has been created for the {@link Run} of the given {@link FlowNode}
     */
    @CheckForNull
    @MustBeClosed
    protected Scope setupContext(WorkflowRun run, @Nonnull FlowNode node) {
        run = verifyNotNull(run, "%s No run found for node %s", run, node);
        Span span = this.otelTraceService.getSpan(run, node);

        Scope scope = span.makeCurrent();
        Context.current().with(RunContextKey.KEY, run).with(FlowNodeContextKey.KEY, node);
        return scope;
    }

    @Inject
    public final void setOpenTelemetryTracerService(@Nonnull OtelTraceService otelTraceService) {
        this.otelTraceService = otelTraceService;
        this.tracer = this.otelTraceService.getTracer();
    }

    @Nonnull
    public OtelTraceService getTracerService() {
        return otelTraceService;
    }

    @Nonnull
    public Tracer getTracer() {
        return tracer;
    }

    @Override
    public String toString() {
        return "TracingPipelineListener{}";
    }
}
