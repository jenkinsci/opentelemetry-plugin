/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

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
import io.jenkins.plugins.opentelemetry.job.step.StepHandler;
import io.jenkins.plugins.opentelemetry.job.step.WithSpanAttributeStep;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.events.EventEmitter;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.semconv.ResourceAttributes;
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
import org.jenkinsci.plugins.workflow.steps.*;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.io.IOException;
import java.util.*;
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
    public void onStartNodeStep(@NonNull StepStartNode stepStartNode, @Nullable String agentLabel, @NonNull WorkflowRun run) {
        try (Scope nodeSpanScope = setupContext(run, stepStartNode)) {
            verifyNotNull(nodeSpanScope, "%s - No span found for node %s", run, stepStartNode);
            String stepType = getStepType(stepStartNode, stepStartNode.getDescriptor(), JenkinsOtelSemanticAttributes.STEP_NODE);
            JenkinsOpenTelemetryPluginConfiguration.StepPlugin stepPlugin = JenkinsOpenTelemetryPluginConfiguration.get().findStepPluginOrDefault(stepType, stepStartNode);

            SpanBuilder agentSpanBuilder = getTracer().spanBuilder(JenkinsOtelSemanticAttributes.AGENT_UI)
                .setParent(Context.current())
                .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_TYPE, stepType)
                .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_ID, stepStartNode.getId())
                .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_NAME, JenkinsOtelSemanticAttributes.AGENT) // FIXME verify it's the right semantic and value
                .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_NAME, stepPlugin.getName())
                .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_VERSION, stepPlugin.getVersion());
            if (agentLabel != null) {
                agentSpanBuilder.setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_AGENT_LABEL, agentLabel);
            }
            Span agentSpan = agentSpanBuilder.startSpan();

            LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - > " + JenkinsOtelSemanticAttributes.AGENT + "(" + agentLabel + ") - begin " + OtelUtils.toDebugString(agentSpan));

            getTracerService().putSpan(run, agentSpan, stepStartNode);

            try (Scope allocateAgentSpanScope = agentSpan.makeCurrent()) {
                SpanBuilder allocateAgentSpanBuilder = getTracer().spanBuilder(JenkinsOtelSemanticAttributes.AGENT_ALLOCATION_UI)
                    .setParent(Context.current())
                    .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_TYPE, getStepType(stepStartNode, stepStartNode.getDescriptor(), JenkinsOtelSemanticAttributes.STEP_NODE))
                    .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_ID, stepStartNode.getId())
                    .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_NAME, JenkinsOtelSemanticAttributes.AGENT_ALLOCATE) // FIXME verify it's the right semantic and value
                    .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_NAME, stepPlugin.getName())
                    .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_VERSION, stepPlugin.getVersion());
                if (agentLabel != null) {
                    allocateAgentSpanBuilder.setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_AGENT_LABEL, agentLabel);
                }
                Span allocateAgentSpan = allocateAgentSpanBuilder.startSpan();

                LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - > " + JenkinsOtelSemanticAttributes.AGENT_ALLOCATE + "(" + agentLabel + ") - begin " + OtelUtils.toDebugString(allocateAgentSpan));

                getTracerService().putSpan(run, allocateAgentSpan, stepStartNode);
            }
        }
    }

    @Override
    public void onAfterStartNodeStep(@NonNull StepStartNode stepStartNode, @Nullable String nodeLabel, @NonNull WorkflowRun run) {
        // end the JenkinsOtelSemanticAttributes.AGENT_ALLOCATE span
        endCurrentSpan(stepStartNode, run);
    }

    @Override
    public void onStartStageStep(@NonNull StepStartNode stepStartNode, @NonNull String stageName, @NonNull WorkflowRun run) {
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
    public void onEndNodeStep(@NonNull StepEndNode node, @NonNull String nodeName, @NonNull WorkflowRun run) {
        endCurrentSpan(node, run);
    }

    @Override
    public void onEndStageStep(@NonNull StepEndNode node, @NonNull String stageName, @NonNull WorkflowRun run) {
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
    public void onAtomicStep(@NonNull StepAtomNode node, @NonNull WorkflowRun run) {
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
    public void onAfterAtomicStep(@NonNull StepAtomNode node, @NonNull WorkflowRun run) {
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
        String stepFunctionName = stepDescriptor.getFunctionName();
        boolean ignoreStep = WithSpanAttributeStep.DescriptorImpl.FUNCTION_NAME.equals(stepFunctionName) || this.ignoredSteps.contains(stepFunctionName);
        LOGGER.log(Level.FINER, ()-> "isIgnoreStep(" + stepDescriptor + "): " + ignoreStep);
        return ignoreStep;
    }

    private String getStepName(@NonNull StepAtomNode node, @NonNull String name) {
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

    private String getStepType(@NonNull FlowNode node, @Nullable StepDescriptor stepDescriptor, @NonNull String type) {
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
    private UninstantiatedDescribable getUninstantiatedDescribableOrNull(@NonNull FlowNode node, @Nullable StepDescriptor stepDescriptor) {
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
    public void onStartParallelStepBranch(@NonNull StepStartNode stepStartNode, @NonNull String branchName, @NonNull WorkflowRun run) {
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
    public void onEndParallelStepBranch(@NonNull StepEndNode node, @NonNull String branchName, @NonNull WorkflowRun run) {
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

                    String statusDescription = throwable.getClass().getSimpleName() + ": " + String.join(", ", causeDescriptions);

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
    public void notifyOfNewStep(@NonNull Step step, @NonNull StepContext context) {
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
    @NonNull
    @MustBeClosed
    protected Scope setupContext(WorkflowRun run, @NonNull FlowNode node) {
        run = verifyNotNull(run, "%s No run found for node %s", run, node);
        Span span = this.otelTraceService.getSpan(run, node);

        return span.makeCurrent();
    }

    @Inject
    public final void setOpenTelemetryTracerService(@NonNull OtelTraceService otelTraceService) {
        this.otelTraceService = otelTraceService;
    }

    @NonNull
    public OtelTraceService getTracerService() {
        return otelTraceService;
    }

    @NonNull
    public Tracer getTracer() {
        return tracer;
    }

    @Override
    public String toString() {
        return "TracingPipelineListener{}";
    }

    @Override
    public void afterSdkInitialized(Meter meter, LoggerProvider loggerProvider, EventEmitter eventEmitter, Tracer tracer, ConfigProperties configProperties) {
        this.tracer = tracer;
        LOGGER.log(Level.FINE, () -> "Start monitoring Jenkins pipeline executions...");
    }

    @Override
    public void beforeSdkShutdown() {

    }
}
