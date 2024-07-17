/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import com.google.common.collect.Iterables;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Computer;
import hudson.model.Describable;
import hudson.model.Descriptor;
import io.jenkins.plugins.opentelemetry.JenkinsControllerOpenTelemetry;
import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetryPluginConfiguration;
import io.jenkins.plugins.opentelemetry.OpenTelemetryAttributesAction;
import io.jenkins.plugins.opentelemetry.OtelUtils;
import io.jenkins.plugins.opentelemetry.api.OpenTelemetryLifecycleListener;
import io.jenkins.plugins.opentelemetry.job.jenkins.PipelineListener;
import io.jenkins.plugins.opentelemetry.job.jenkins.PipelineNodeUtil;
import io.jenkins.plugins.opentelemetry.job.step.SetSpanAttributesStep;
import io.jenkins.plugins.opentelemetry.job.step.StepHandler;
import io.jenkins.plugins.opentelemetry.job.step.WithSpanAttributeStep;
import io.jenkins.plugins.opentelemetry.job.step.WithSpanAttributesStep;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.incubating.HostIncubatingAttributes;
import jenkins.YesNoMaybe;
import jenkins.model.CauseOfInterruption;
import org.jenkinsci.plugins.structs.SymbolLookup;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.StepListener;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graph.StepNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.pipelinegraphanalysis.GenericStatus;
import org.jenkinsci.plugins.workflow.pipelinegraphanalysis.StatusAndTiming;
import org.jenkinsci.plugins.workflow.steps.CatchErrorStep;
import org.jenkinsci.plugins.workflow.steps.CoreStep;
import org.jenkinsci.plugins.workflow.steps.EnvStep;
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.support.steps.ExecutorStep;
import org.jenkinsci.plugins.workflow.support.steps.StageStep;

import javax.annotation.Nonnull;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.google.common.base.Verify.verifyNotNull;


@Extension(dynamicLoadable = YesNoMaybe.YES, optional = true)
public class MonitoringPipelineListener implements PipelineListener, StepListener, OpenTelemetryLifecycleListener {
    private final static Logger LOGGER = Logger.getLogger(MonitoringPipelineListener.class.getName());

    private OtelTraceService otelTraceService;
    private Tracer tracer;
    private Set<String> ignoredSteps;
    private List<StepHandler> stepHandlers;

    /**
     * Interruption causes that should mark the span as error because they are external interruptions.
     */
    Set<String> statusUnsetCausesOfInterruption;

    @Inject
    protected JenkinsControllerOpenTelemetry jenkinsControllerOpenTelemetry;

    @PostConstruct
    public void postConstruct() {
        LOGGER.log(Level.FINE, () -> "Start monitoring Jenkins pipeline executions...");
        this.tracer = jenkinsControllerOpenTelemetry.getDefaultTracer();

        final JenkinsOpenTelemetryPluginConfiguration jenkinsOpenTelemetryPluginConfiguration = JenkinsOpenTelemetryPluginConfiguration.get();
        this.ignoredSteps = new HashSet<>(Arrays.asList(jenkinsOpenTelemetryPluginConfiguration.getIgnoredSteps().split(",")));
        this.statusUnsetCausesOfInterruption = new HashSet<>(jenkinsOpenTelemetryPluginConfiguration.getStatusUnsetCausesOfInterruption());
    }

    @Override
    public void onStartNodeStep(@NonNull StepStartNode node, @Nullable String agentLabel, @NonNull WorkflowRun run) {
        Span encapsulatingSpan = this.otelTraceService.getSpan(run, node);
        String stepType = getStepType(node, node.getDescriptor(), JenkinsOtelSemanticAttributes.STEP_NODE);
        JenkinsOpenTelemetryPluginConfiguration.StepPlugin stepPlugin = JenkinsOpenTelemetryPluginConfiguration.get().findStepPluginOrDefault(stepType, node);

        SpanBuilder agentSpanBuilder = getTracer().spanBuilder(JenkinsOtelSemanticAttributes.AGENT_UI)
            .setParent(Context.current().with(encapsulatingSpan))
            .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_TYPE, stepType)
            .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_ID, node.getId())
            .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_NAME, JenkinsOtelSemanticAttributes.AGENT) // FIXME verify it's the right semantic and value
            .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_NAME, stepPlugin.getName())
            .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_VERSION, stepPlugin.getVersion());
        if (agentLabel != null) {
            agentSpanBuilder.setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_AGENT_LABEL, agentLabel);
        }
        Span agentSpan = agentSpanBuilder.startSpan();

        LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - > " + JenkinsOtelSemanticAttributes.AGENT + "(" + agentLabel + ") - begin " + OtelUtils.toDebugString(agentSpan));

        getTracerService().putAgentSpan(run, agentSpan, node);

        SpanBuilder allocateAgentSpanBuilder = getTracer().spanBuilder(JenkinsOtelSemanticAttributes.AGENT_ALLOCATION_UI)
            .setParent(Context.current().with(agentSpan))
            .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_TYPE, getStepType(node, node.getDescriptor(), JenkinsOtelSemanticAttributes.STEP_NODE))
            .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_ID, node.getId())
            .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_NAME, JenkinsOtelSemanticAttributes.AGENT_ALLOCATE) // FIXME verify it's the right semantic and value
            .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_NAME, stepPlugin.getName())
            .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_VERSION, stepPlugin.getVersion());
        if (agentLabel != null) {
            allocateAgentSpanBuilder.setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_AGENT_LABEL, agentLabel);
        }
        Span allocateAgentSpan = allocateAgentSpanBuilder.startSpan();

        LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - > " + JenkinsOtelSemanticAttributes.AGENT_ALLOCATE + "(" + agentLabel + ") - begin " + OtelUtils.toDebugString(allocateAgentSpan));

        getTracerService().putAgentSpan(run, allocateAgentSpan, node);
    }

    @Override
    public void onAfterStartNodeStep(@NonNull StepStartNode stepStartNode, @Nullable String nodeLabel, @NonNull WorkflowRun run) {
        // end the JenkinsOtelSemanticAttributes.AGENT_ALLOCATE span
        endCurrentSpan(stepStartNode, run, null);
    }

    @Override
    public void onStartStageStep(@NonNull StepStartNode node, @NonNull String stageName, @NonNull WorkflowRun run) {
        Span encapsulatingSpan = this.otelTraceService.getSpan(run, node);

        String spanStageName = "Stage: " + stageName;

        String stepType = getStepType(node, node.getDescriptor(), "stage");
        JenkinsOpenTelemetryPluginConfiguration.StepPlugin stepPlugin = JenkinsOpenTelemetryPluginConfiguration.get().findStepPluginOrDefault(stepType, node);

        Span stageSpan = getTracer().spanBuilder(spanStageName)
            .setParent(Context.current().with(encapsulatingSpan))
            .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_TYPE, stepType)
            .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_ID, node.getId())
            .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_NAME, stageName)
            .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_NAME, stepPlugin.getName())
            .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_VERSION, stepPlugin.getVersion())
            .startSpan();
        LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - > stage(" + stageName + ") - begin " + OtelUtils.toDebugString(stageSpan));

        getTracerService().putSpan(run, stageSpan, node);

    }

    @Override
    public void onEndNodeStep(@NonNull StepEndNode node, @NonNull String nodeName, FlowNode nextNode, @NonNull WorkflowRun run) {
        StepStartNode nodeStartNode = node.getStartNode();
        GenericStatus nodeStatus = StatusAndTiming.computeChunkStatus2(run, null, nodeStartNode, node, nextNode);
        endCurrentSpan(node, run, nodeStatus);
    }

    @Override
    public void onEndStageStep(@NonNull StepEndNode node, @NonNull String stageName, FlowNode nextNode, @NonNull WorkflowRun run) {
        StepStartNode stageStartNode = node.getStartNode();
        GenericStatus stageStatus = StatusAndTiming.computeChunkStatus2(run, null, stageStartNode, node, nextNode);
        endCurrentSpan(node, run, stageStatus);
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
        if (isIgnoredStep(node.getDescriptor())) {
            LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - don't create span for step '" + node.getDisplayFunctionName() + "'");
            return;
        }
        Span encapsulatingSpan = this.otelTraceService.getSpan(run, node);

        String principal = Objects.toString(node.getExecution().getAuthentication().getPrincipal(), "#null#");

        StepHandler stepHandler = getStepHandlers().stream().filter(sh -> sh.canCreateSpanBuilder(node, run)).findFirst()
            .orElseThrow((Supplier<RuntimeException>) () ->
                new IllegalStateException("No StepHandler found for node " + node.getClass() + " - " + node + " on " + run));
        SpanBuilder spanBuilder = stepHandler.createSpanBuilder(node, run, getTracer());

        String stepType = getStepType(node, node.getDescriptor(), JenkinsOtelSemanticAttributes.STEP_NAME);
        JenkinsOpenTelemetryPluginConfiguration.StepPlugin stepPlugin = JenkinsOpenTelemetryPluginConfiguration.get().findStepPluginOrDefault(stepType, node);

        spanBuilder
            .setParent(Context.current().with(encapsulatingSpan))
            .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_TYPE, stepType)
            .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_ID, node.getId())
            .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_NAME, getStepName(node, JenkinsOtelSemanticAttributes.STEP_NAME))
            .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_USER, principal)
            .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_NAME, stepPlugin.getName())
            .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_VERSION, stepPlugin.getVersion());

        Span atomicStepSpan = spanBuilder.startSpan();
        LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - > " + node.getDisplayFunctionName() + " - begin " + OtelUtils.toDebugString(atomicStepSpan));
        Scope atomicStepScope = atomicStepSpan.makeCurrent();
        stepHandler.afterSpanCreated(node, run);

        getTracerService().putSpanAndScopes(run, atomicStepSpan, node, atomicStepScope);
    }


    @Override
    public void onAfterAtomicStep(@NonNull StepAtomNode node, FlowNode nextNode, @NonNull WorkflowRun run) {
        if (isIgnoredStep(node.getDescriptor())) {
            LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - don't end span for step '" + node.getDisplayFunctionName() + "'");
            return;
        }
        GenericStatus stageStatus = StatusAndTiming.computeChunkStatus2(run, null, node, node, nextNode);
        endCurrentSpan(node, run, stageStatus);
    }

    private boolean isIgnoredStep(@Nullable StepDescriptor stepDescriptor) {
        if (stepDescriptor == null) {
            return true;
        }
        String stepFunctionName = stepDescriptor.getFunctionName();
        boolean ignoreStep = SetSpanAttributesStep.DescriptorImpl.FUNCTION_NAME.equals(stepFunctionName)
            || WithSpanAttributeStep.DescriptorImpl.FUNCTION_NAME.equals(stepFunctionName)
            || WithSpanAttributesStep.DescriptorImpl.FUNCTION_NAME.equals(stepFunctionName)
            || this.ignoredSteps.contains(stepFunctionName);
        LOGGER.log(Level.FINER, () -> "isIgnoreStep(" + stepDescriptor + "): " + ignoreStep);
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

    /**
     * @param node
     * @param stepDescriptor
     * @param defaultType    default ype if the passed stepDescriptor is {@code null}
     * @return
     */
    private String getStepType(@NonNull FlowNode node, @Nullable StepDescriptor stepDescriptor, @NonNull String defaultType) {
        if (stepDescriptor == null) {
            return defaultType;
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
    public void onStartParallelStepBranch(@NonNull StepStartNode node, @NonNull String branchName, @NonNull WorkflowRun run) {
        Span encapsulatingSpan = this.otelTraceService.getSpan(run, node);

        String stepType = getStepType(node, node.getDescriptor(), "branch");
        JenkinsOpenTelemetryPluginConfiguration.StepPlugin stepPlugin = JenkinsOpenTelemetryPluginConfiguration.get().findStepPluginOrDefault(stepType, node);

        Span atomicStepSpan = getTracer().spanBuilder("Parallel branch: " + branchName)
            .setParent(Context.current().with(encapsulatingSpan))
            .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_TYPE, stepType)
            .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_ID, node.getId())
            .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_NAME, branchName)
            .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_NAME, stepPlugin.getName())
            .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_VERSION, stepPlugin.getVersion())
            .startSpan();
        LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - > parallel branch(" + branchName + ") - begin " + OtelUtils.toDebugString(atomicStepSpan));

        getTracerService().putSpan(run, atomicStepSpan, node);
    }

    @Override
    public void onEndParallelStepBranch(@NonNull StepEndNode node, @NonNull String branchName, FlowNode nextNode, @NonNull WorkflowRun run) {
        StepStartNode parallelStartNode = node.getStartNode();
        GenericStatus parallelStatus = StatusAndTiming.computeChunkStatus2(run, null, parallelStartNode, node, nextNode);
        endCurrentSpan(node, run, parallelStatus);
    }

    @Override
    public void onOnOtherBlockStepStartNode(@Nonnull StepStartNode node, @Nonnull WorkflowRun run) {
        if (isSkipBlockStepNode(node)) {
            return;
        }
        Span encapsulatingSpan = this.otelTraceService.getSpan(run, node);

        String stepType = getStepType(node, node.getDescriptor(), "block-step");
        JenkinsOpenTelemetryPluginConfiguration.StepPlugin stepPlugin = JenkinsOpenTelemetryPluginConfiguration.get().findStepPluginOrDefault(stepType, node);

        Span atomicStepSpan = getTracer().spanBuilder(stepType)
            .setParent(Context.current().with(encapsulatingSpan))
            .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_TYPE, "descriptor.class=" + node.getDescriptor().getClass() + ", descriptor.functionName=" + node.getDescriptor().getFunctionName() + ", stepStartNode.isBody=" + node.isBody())
            .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_ID, node.getId())
            .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_NAME, stepType)
            .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_NAME, stepPlugin.getName())
            .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_VERSION, stepPlugin.getVersion())
            .startSpan();
        LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - >b lock step(" + stepType + ") - begin " + OtelUtils.toDebugString(atomicStepSpan));

        getTracerService().putSpan(run, atomicStepSpan, node);
    }

    boolean isSkipBlockStepNode(@NonNull StepStartNode node) {

        // FIXME why isn't this implementation working?
        Predicate<? super StepDescriptor> isIgnoredBlockStepPredicate = (Predicate<StepDescriptor>) stepDescriptor -> stepDescriptor instanceof StageStep.DescriptorImpl ||
            stepDescriptor instanceof EnvStep.DescriptorImpl ||
            stepDescriptor instanceof ExecutorStep.DescriptorImpl;
        boolean isSkippedBlockStep = Optional
            .ofNullable(node)
            .filter(Predicate.not(StepStartNode::isBody))
            .map(StepStartNode::getDescriptor)
            .filter(isIgnoredBlockStepPredicate)
            .isPresent();
        final boolean isSkippedBlockStep2;
        if (node.isBody()) {
            isSkippedBlockStep2 = true;
        } else {
            StepDescriptor stepDescriptor = node.getDescriptor();
            isSkippedBlockStep2 = stepDescriptor instanceof StageStep.DescriptorImpl ||
                stepDescriptor instanceof EnvStep.DescriptorImpl ||
                stepDescriptor instanceof ExecutorStep.DescriptorImpl ||
                stepDescriptor instanceof WithSpanAttributesStep.DescriptorImpl ||
                stepDescriptor instanceof WithSpanAttributeStep.DescriptorImpl ||
                stepDescriptor instanceof CatchErrorStep.DescriptorImpl;
        }
        if (isSkippedBlockStep != isSkippedBlockStep2) {
            LOGGER.log(Level.FINE, () -> "isSkipBlockStepNode(" + node.getId() + "): " + isSkippedBlockStep + " != " + isSkippedBlockStep2);
        }
        LOGGER.log(Level.FINE, () -> "Ignore block step " + Optional.ofNullable(node)
            .map(ssn -> "node.stepName=" + ssn.getStepName() +
                ", node.id=" + ssn.getId() +
                ", node.isBody=" + ssn.isBody() +
                ", descriptor.functionName=" + ssn.getDescriptor().getFunctionName() +
                ", descriptor.class=" + ssn.getDescriptor().getClass())
            .orElse("null") + " - " + isSkippedBlockStep);
        return isSkippedBlockStep2;
    }

    @Override
    public void onAfterOtherBlockStepStartNode(@NonNull StepStartNode node, @NonNull WorkflowRun run) {
        if (isSkipBlockStepNode(node)) {
            return;
        }
        debug(node, run, "onAfterOtherBlockStepStartNode");
    }

    @Override
    public void onOtherBlockStepEndNode(@NonNull StepEndNode node, @NonNull WorkflowRun run) {
        if (isSkipBlockStepNode(node.getStartNode())) {
            return;
        }
        debug(node, run, "onOtherBlockStepEndNode");
    }

    @Override
    public void onAfterOtherBlockStepEndNode(@NonNull StepEndNode node, @NonNull WorkflowRun run) {
        if (isSkipBlockStepNode(node.getStartNode())) {
            return;
        }
        debug(node, run, "onAfterOtherBlockStepEndNode");
        StepStartNode parallelStartNode = node.getStartNode();
        FlowNode nextNode = null; // FIXME get next node
        GenericStatus parallelStatus = StatusAndTiming.computeChunkStatus2(run, null, parallelStartNode, node, nextNode);
        endCurrentSpan(node, run, parallelStatus);
    }

    private static void debug(@Nonnull FlowNode node, @Nonnull WorkflowRun run, String type) {
        LOGGER.log(Level.INFO, () ->
        {

            String message = run.getFullDisplayName() + " - " + type + " - " +
                node.getDisplayFunctionName() + " // " + PipelineNodeUtil.getDisplayName(node) + ", ";

            if (node instanceof StepNode && ((StepNode) node).getDescriptor() != null) {
                StepDescriptor descriptor = ((StepNode) node).getDescriptor();
                message += "descriptor (class:" + descriptor.getClass().getName() + ", " + descriptor.getFunctionName() + "), ";
            }
            message += node.getAllActions().stream().map(action -> Objects.toString(action.getDisplayName(), action.getClass().toString())).collect(Collectors.joining(", "));
            message += ", node.parent: " + Iterables.getFirst(node.getParents(), null);
            message += ", thread: " + Thread.currentThread().getName();
            return message;
        });
    }

    private void endCurrentSpan(@NonNull FlowNode node, @NonNull WorkflowRun run, @Nullable GenericStatus status) {
        Span span = getTracerService().getSpan(run, node);

        ErrorAction errorAction = node.getError();
        if (errorAction == null) {
            if (status == null) status = GenericStatus.SUCCESS;
            span.setStatus(StatusCode.OK);
        } else {
            Throwable throwable = errorAction.getError();
            if (throwable instanceof FlowInterruptedException) {
                FlowInterruptedException interruptedException = (FlowInterruptedException) throwable;
                List<CauseOfInterruption> causesOfInterruption = interruptedException.getCauses();

                if (status == null) status = GenericStatus.fromResult(interruptedException.getResult());

                List<String> causeDescriptions = causesOfInterruption.stream().map(cause -> cause.getClass().getSimpleName() + ": " + cause.getShortDescription()).collect(Collectors.toList());
                span.setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_INTERRUPTION_CAUSES, causeDescriptions);

                String statusDescription = throwable.getClass().getSimpleName() + ": " + String.join(", ", causeDescriptions);

                boolean suppressSpanStatusCodeError = false;
                for (CauseOfInterruption causeOfInterruption : causesOfInterruption) {
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
                if (status == null) status = GenericStatus.FAILURE;
                span.recordException(throwable);
                span.setStatus(StatusCode.ERROR, throwable.getMessage());
            }
        }

        if (status != null) {
            status = StatusAndTiming.coerceStatusApi(status, StatusAndTiming.API_V2);
            span.setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_RESULT, status.toString());
        }

        span.end();
        LOGGER.log(Level.FINE, () -> run.getFullDisplayName() + " - < " + node.getDisplayFunctionName() + " - end " + OtelUtils.toDebugString(span));

        getTracerService().removePipelineStepSpanAndCloseAssociatedScopes(run, node, span);

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
                LOGGER.log(Level.WARNING, "Unexpected missing " + OpenTelemetryAttributesAction.class + " on " + computer + ", adding fallback");
                String hostName = computer.getHostName();
                OpenTelemetryAttributesAction openTelemetryAttributesAction = new OpenTelemetryAttributesAction();
                if (hostName != null) {
                    // getHostName() returns null if the master cannot find the host name, e.g. due to network settings.
                    // @see hudson.model.Computer#getHostName()
                    openTelemetryAttributesAction.getAttributes().put(HostIncubatingAttributes.HOST_NAME, hostName);
                }
                openTelemetryAttributesAction.getAttributes().put(AttributeKey.stringKey(JenkinsOtelSemanticAttributes.JENKINS_COMPUTER_NAME.getKey()), computer.getName());
                computer.addAction(openTelemetryAttributesAction);
            }
            OpenTelemetryAttributesAction otelComputerAttributesAction = computer.getAction(OpenTelemetryAttributesAction.class);
            OpenTelemetryAttributesAction otelChildAttributesAction = context.get(OpenTelemetryAttributesAction.class);

            Span currentSpan = this.otelTraceService.getSpan(run, node);

            LOGGER.log(Level.FINE, () -> "Add resource attributes to span " + OtelUtils.toDebugString(currentSpan) + " - " + otelComputerAttributesAction);
            setAttributesToSpan(currentSpan, otelComputerAttributesAction);

            LOGGER.log(Level.FINE, () -> "Add attributes to child span " + OtelUtils.toDebugString(currentSpan) + " - " + otelChildAttributesAction);
            setAttributesToSpan(currentSpan, otelChildAttributesAction);

        } catch (IOException | InterruptedException | RuntimeException e) {
            LOGGER.log(Level.WARNING, "Exception processing " + step + " - " + context, e);
        }
    }

    private void setAttributesToSpan(@NonNull Span span, OpenTelemetryAttributesAction openTelemetryAttributesAction) {
        if (openTelemetryAttributesAction == null) {
            return;
        }
        if (!openTelemetryAttributesAction.isNotYetAppliedToSpan(span.getSpanContext().getSpanId())) {
            // Do not reapply attributes, if previously applied.
            // This is important for overriding of attributes to work in an intuitive manner.
            return;
        }
        for (Map.Entry<AttributeKey<?>, Object> entry : openTelemetryAttributesAction.getAttributes().entrySet()) {
            AttributeKey<?> attributeKey = entry.getKey();
            Object value = verifyNotNull(entry.getValue());
            span.setAttribute((AttributeKey<? super Object>) attributeKey, value);
        }
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
        return "MonitoringPipelineListener{}";
    }
}
