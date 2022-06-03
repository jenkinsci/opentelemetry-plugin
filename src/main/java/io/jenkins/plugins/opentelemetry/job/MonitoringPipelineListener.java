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
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Run;
import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetryPluginConfiguration;
import io.jenkins.plugins.opentelemetry.OpenTelemetryAttributesAction;
import io.jenkins.plugins.opentelemetry.OtelComponent;
import io.jenkins.plugins.opentelemetry.OtelUtils;
import io.jenkins.plugins.opentelemetry.job.jenkins.AbstractPipelineListener;
import io.jenkins.plugins.opentelemetry.job.jenkins.PipelineListener;
import io.jenkins.plugins.opentelemetry.job.opentelemetry.context.FlowNodeContextKey;
import io.jenkins.plugins.opentelemetry.job.opentelemetry.context.RunContextKey;
import io.jenkins.plugins.opentelemetry.job.step.StepHandler;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.logs.LogEmitter;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import jenkins.YesNoMaybe;
import jenkins.model.CauseOfInterruption;
import org.apache.commons.compress.utils.Sets;
import org.jenkinsci.plugins.structs.SymbolLookup;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.StepListener;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.CoreStep;
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.google.common.base.Verify.verifyNotNull;


@Extension(dynamicLoadable = YesNoMaybe.YES, optional = true)
public class MonitoringPipelineListener extends AbstractPipelineListener implements PipelineListener, StepListener, OtelComponent {
    private final static Logger LOGGER = Logger.getLogger(MonitoringPipelineListener.class.getName());

    private OtelTraceService otelTraceService;
    private Tracer tracer;
    private Set<String> ignoredSteps;
    private List<StepHandler> stepHandlers;

    /**
     * Interruption causes that should mark the span as error because they are external interruptions.
     */
    Set<String> statusUnsetCausesOfInterruption;

    @PostConstruct
    public void postConstruct() {
        final JenkinsOpenTelemetryPluginConfiguration jenkinsOpenTelemetryPluginConfiguration = JenkinsOpenTelemetryPluginConfiguration.get();
        this.ignoredSteps = Sets.newHashSet(jenkinsOpenTelemetryPluginConfiguration.getIgnoredSteps().split(","));
        this.statusUnsetCausesOfInterruption = new HashSet<>(jenkinsOpenTelemetryPluginConfiguration.getStatusUnsetCausesOfInterruption());
    }

    @Override
    public void onStartNodeStep(@Nonnull StepStartNode stepStartNode, @Nullable String agentLabel, @Nonnull WorkflowRun run) {
        try (Scope nodeSpanScope = setupContext(run, stepStartNode)) {
            verifyNotNull(nodeSpanScope, "%s - No span found for node %s", run, stepStartNode);
            String stepType = getStepType(stepStartNode, stepStartNode.getDescriptor(), JenkinsOtelSemanticAttributes.STEP_NODE);
            JenkinsOpenTelemetryPluginConfiguration.StepPlugin stepPlugin = JenkinsOpenTelemetryPluginConfiguration.get().findStepPluginOrDefault(stepType, stepStartNode);

            Span agentSpan = getTracer().spanBuilder(JenkinsOtelSemanticAttributes.AGENT_UI)
                    .setParent(Context.current())
                    .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_TYPE, stepType)
                    .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_ID, stepStartNode.getId())
                    .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_NAME, JenkinsOtelSemanticAttributes.AGENT) // FIXME verify it's the right semantic and value
                    .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_AGENT_LABEL, Strings.emptyToNull(agentLabel))
                    .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_NAME, stepPlugin.getName())
                    .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_VERSION, stepPlugin.getVersion())
                    .startSpan();
            LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - > " + JenkinsOtelSemanticAttributes.AGENT + "(" + agentLabel + ") - begin " + OtelUtils.toDebugString(agentSpan));

            getTracerService().putSpan(run, agentSpan, stepStartNode);

            try (Scope allocateAgentSpanScope = agentSpan.makeCurrent()) {
                Span allocateAgentSpan = getTracer().spanBuilder(JenkinsOtelSemanticAttributes.AGENT_ALLOCATION_UI)
                        .setParent(Context.current())
                        .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_TYPE, getStepType(stepStartNode, stepStartNode.getDescriptor(), JenkinsOtelSemanticAttributes.STEP_NODE))
                        .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_ID, stepStartNode.getId())
                        .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_NAME, JenkinsOtelSemanticAttributes.AGENT_ALLOCATE) // FIXME verify it's the right semantic and value
                        .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_AGENT_LABEL, Strings.emptyToNull(agentLabel))
                        .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_NAME, stepPlugin.getName())
                        .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_VERSION, stepPlugin.getVersion())
                        .startSpan();
                LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - > " + JenkinsOtelSemanticAttributes.AGENT_ALLOCATE + "(" + agentLabel + ") - begin " + OtelUtils.toDebugString(allocateAgentSpan));

                getTracerService().putSpan(run, allocateAgentSpan, stepStartNode);
            }
        }
    }

    @Override
    public void onAfterStartNodeStep(@Nonnull StepStartNode stepStartNode, @Nullable String nodeLabel, @Nonnull WorkflowRun run) {
        // end the JenkinsOtelSemanticAttributes.AGENT_ALLOCATE span
        endCurrentSpan(stepStartNode, run);
    }

    @Override
    public void onStartStageStep(@Nonnull StepStartNode stepStartNode, @Nonnull String stageName, @Nonnull WorkflowRun run) {
        try (Scope ignored = setupContext(run, stepStartNode)) {
            verifyNotNull(ignored, "%s - No span found for node %s", run, stepStartNode);
            String spanStageName = "Stage: " + stageName;

            String stepType = getStepType(stepStartNode, stepStartNode.getDescriptor(),"stage");
            JenkinsOpenTelemetryPluginConfiguration.StepPlugin stepPlugin = JenkinsOpenTelemetryPluginConfiguration.get().findStepPluginOrDefault(stepType, stepStartNode);

            Span stageSpan = getTracer().spanBuilder(spanStageName)
                    .setParent(Context.current())
                    .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_TYPE, stepType)
                    .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_ID, stepStartNode.getId())
                    .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_NAME, stageName)
                    .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_NAME, stepPlugin.getName())
                    .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_VERSION, stepPlugin.getVersion())
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

    protected List<StepHandler> getStepHandlers() {
        if (stepHandlers == null) {
            List<StepHandler> stepHandlers = new ArrayList<>(ExtensionList.lookup(StepHandler.class));
            Collections.sort(stepHandlers);
            this.stepHandlers = stepHandlers;
        }
        return this.stepHandlers;
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

            StepHandler stepHandler = getStepHandlers().stream().filter(sh -> sh.canCreateSpanBuilder(node, run)).findFirst()
                .orElseThrow((Supplier<RuntimeException>) () ->
                    new IllegalStateException("No StepHandler found for node " + node.getClass() + " - " + node + " on " + run));
            SpanBuilder spanBuilder = stepHandler.createSpanBuilder(node, run, getTracer());

            String stepType = getStepType(node, node.getDescriptor(), JenkinsOtelSemanticAttributes.STEP_NAME);
            JenkinsOpenTelemetryPluginConfiguration.StepPlugin stepPlugin = JenkinsOpenTelemetryPluginConfiguration.get().findStepPluginOrDefault(stepType, node);

            spanBuilder
                    .setParent(Context.current()) // TODO can we remove this call?
                    .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_TYPE, stepType)
                    .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_ID, node.getId())
                    .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_NAME, getStepName(node, JenkinsOtelSemanticAttributes.STEP_NAME))
                    .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_USER, principal)
                    .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_NAME, stepPlugin.getName())
                    .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_VERSION, stepPlugin.getVersion());

            Span atomicStepSpan = spanBuilder.startSpan();
            LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - > " + node.getDisplayFunctionName() + " - begin " + OtelUtils.toDebugString(atomicStepSpan));
            try (Scope ignored2 = atomicStepSpan.makeCurrent()) {
                stepHandler.afterSpanCreated(node, run);
            }
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

    private String getStepName(@Nonnull StepAtomNode node, @Nonnull String name) {
        StepDescriptor stepDescriptor = node.getDescriptor();
        if (stepDescriptor == null) {
            return name;
        }
        UninstantiatedDescribable describable = getUninstantiatedDescribableOrNull(node, stepDescriptor);
        if (describable != null) {
            Descriptor<? extends Describable> d = SymbolLookup.get().findDescriptor(Describable.class, describable.getSymbol());
            return d.getDisplayName();
        }
        return stepDescriptor.getDisplayName();
    }

    private String getStepType(@Nonnull FlowNode node, @Nullable StepDescriptor stepDescriptor, @Nonnull String type) {
        if (stepDescriptor == null) {
            return type;
        }
        UninstantiatedDescribable describable = getUninstantiatedDescribableOrNull(node, stepDescriptor);
        if (describable != null) {
            return describable.getSymbol();
        }
        return stepDescriptor.getFunctionName();
    }

    @Nullable
    private UninstantiatedDescribable getUninstantiatedDescribableOrNull(@Nonnull FlowNode node, @Nullable StepDescriptor stepDescriptor) {
        // Support for https://javadoc.jenkins.io/jenkins/tasks/SimpleBuildStep.html
        if (stepDescriptor instanceof CoreStep.DescriptorImpl) {
            Map<String, Object> arguments = ArgumentsAction.getFilteredArguments(node);
            if (arguments.get("delegate") instanceof UninstantiatedDescribable) {
              return (UninstantiatedDescribable) arguments.get("delegate");
            }
        }
        return null;
    }

    @Override
    public void onStartParallelStepBranch(@Nonnull StepStartNode stepStartNode, @Nonnull String branchName, @Nonnull WorkflowRun run) {
        try (Scope ignored = setupContext(run, stepStartNode)) {
            verifyNotNull(ignored, "%s - No span found for node %s", run, stepStartNode);

            String stepType = getStepType(stepStartNode, stepStartNode.getDescriptor(),"branch");
            JenkinsOpenTelemetryPluginConfiguration.StepPlugin stepPlugin = JenkinsOpenTelemetryPluginConfiguration.get().findStepPluginOrDefault(stepType, stepStartNode);

            Span atomicStepSpan = getTracer().spanBuilder("Parallel branch: " + branchName)
                    .setParent(Context.current())
                    .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_TYPE, stepType)
                    .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_ID, stepStartNode.getId())
                    .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_NAME, branchName)
                    .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_NAME, stepPlugin.getName())
                    .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_VERSION, stepPlugin.getVersion())
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
                if (throwable instanceof FlowInterruptedException) {
                    FlowInterruptedException interruptedException = (FlowInterruptedException) throwable;
                    List<CauseOfInterruption> causesOfInterruption = interruptedException.getCauses();

                    List<String> causeDescriptions = causesOfInterruption.stream().map(cause -> cause.getClass().getSimpleName() + ": " + cause.getShortDescription()).collect(Collectors.toList());
                    span.setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_INTERRUPTION_CAUSES, causeDescriptions);

                    String statusDescription = throwable.getClass().getSimpleName() + ": " + causeDescriptions.stream().collect(Collectors.joining(", "));

                    boolean suppressSpanStatusCodeError = false;
                    for (CauseOfInterruption causeOfInterruption: causesOfInterruption) {
                        if (statusUnsetCausesOfInterruption.contains(causeOfInterruption.getClass().getName())) {
                            suppressSpanStatusCodeError = true;
                            break;
                        }
                    }
                    if (suppressSpanStatusCodeError) {
                        span.setStatus(StatusCode.UNSET, statusDescription);
                    } else {
                        span.recordException(throwable);
                        span.setStatus(StatusCode.ERROR, statusDescription);
                    }
                } else {
                    span.recordException(throwable);
                    span.setStatus(StatusCode.ERROR, throwable.getMessage());
                }
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

    @Override
    public void afterSdkInitialized(Meter meter, LogEmitter logEmitter, Tracer tracer, ConfigProperties configProperties) {
        this.tracer = tracer;
        LOGGER.log(Level.FINE, () -> "Start monitoring Jenkins pipeline executions...");
    }

    @Override
    public void beforeSdkShutdown() {

    }
}
