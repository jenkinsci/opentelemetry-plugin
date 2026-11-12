/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import static com.google.common.base.Verify.verifyNotNull;

import com.google.common.base.Strings;
import com.google.errorprone.annotations.MustBeClosed;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Computer;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Run;
import io.jenkins.plugins.opentelemetry.JenkinsControllerOpenTelemetry;
import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetryPluginConfiguration;
import io.jenkins.plugins.opentelemetry.OpenTelemetryAttributesAction;
import io.jenkins.plugins.opentelemetry.OtelUtils;
import io.jenkins.plugins.opentelemetry.api.OpenTelemetryLifecycleListener;
import io.jenkins.plugins.opentelemetry.job.action.FlowNodeMonitoringAction;
import io.jenkins.plugins.opentelemetry.job.jenkins.AbstractPipelineListener;
import io.jenkins.plugins.opentelemetry.job.step.BlockStepHandler;
import io.jenkins.plugins.opentelemetry.job.step.SetSpanAttributesStep;
import io.jenkins.plugins.opentelemetry.job.step.SpanAttribute;
import io.jenkins.plugins.opentelemetry.job.step.StepHandler;
import io.jenkins.plugins.opentelemetry.job.step.WithSpanAttributeStep;
import io.jenkins.plugins.opentelemetry.job.step.WithSpanAttributesStep;
import io.jenkins.plugins.opentelemetry.semconv.CicdMetrics;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.jenkins.plugins.opentelemetry.semconv.SemConvStability;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.incubating.CicdIncubatingAttributes;
import io.opentelemetry.semconv.incubating.HostIncubatingAttributes;
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
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import jenkins.YesNoMaybe;
import jenkins.model.CauseOfInterruption;
import org.jenkinsci.plugins.structs.SymbolLookup;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.actions.TimingAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.StepListener;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.pipelinegraphanalysis.GenericStatus;
import org.jenkinsci.plugins.workflow.pipelinegraphanalysis.StatusAndTiming;
import org.jenkinsci.plugins.workflow.steps.CoreStep;
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;

@Extension(dynamicLoadable = YesNoMaybe.YES, optional = true)
public class MonitoringPipelineListener extends AbstractPipelineListener
        implements StepListener, OpenTelemetryLifecycleListener {
    private static final Logger LOGGER = Logger.getLogger(MonitoringPipelineListener.class.getName());

    /** Steps that the plugin gives a span in its own way: a name listed in "block steps" is ignored for them. */
    private static final Set<String> STEPS_WITH_THEIR_OWN_SPAN = Set.of("node", "stage", "parallel", "withNewSpan");

    /** The names of a comma-separated setting, parsed once for the value of the setting that they come from. */
    private record ParsedStepNames(@Nullable String source, Set<String> names) {}

    private OtelTraceService otelTraceService;
    private Tracer tracer;
    private volatile ParsedStepNames ignoredSteps = new ParsedStepNames(null, Set.of());
    private volatile ParsedStepNames blockSteps = new ParsedStepNames(null, Set.of());
    private List<StepHandler> stepHandlers;
    private SemConvStability semConvStability;

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

        final JenkinsOpenTelemetryPluginConfiguration jenkinsOpenTelemetryPluginConfiguration =
                JenkinsOpenTelemetryPluginConfiguration.get();
        this.statusUnsetCausesOfInterruption =
                new HashSet<>(jenkinsOpenTelemetryPluginConfiguration.getStatusUnsetCausesOfInterruption());
        this.semConvStability = jenkinsOpenTelemetryPluginConfiguration.getSemConvStability();
    }

    @Override
    public void onStartNodeStep(
            @NonNull StepStartNode stepStartNode, @Nullable String agentLabel, @NonNull WorkflowRun run) {
        try (Scope nodeSpanScope = setupContext(run, stepStartNode)) {
            verifyNotNull(nodeSpanScope, "%s - No span found for node %s", run, stepStartNode);
            String stepType =
                    getStepType(stepStartNode, stepStartNode.getDescriptor(), ExtendedJenkinsAttributes.STEP_NODE);
            JenkinsOpenTelemetryPluginConfiguration.StepPlugin stepPlugin =
                    JenkinsOpenTelemetryPluginConfiguration.get().findStepPluginOrDefault(stepType, stepStartNode);

            SpanBuilder agentSpanBuilder = getTracer()
                    .spanBuilder(ExtendedJenkinsAttributes.AGENT_UI)
                    .setParent(Context.current())
                    .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_ID, stepStartNode.getId())
                    .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_PLUGIN_NAME, stepPlugin.getName())
                    .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_PLUGIN_VERSION, stepPlugin.getVersion());
            if (semConvStability.emitLegacyCicdSemConv()) {
                agentSpanBuilder
                        .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_TYPE, stepType)
                        .setAttribute(
                                ExtendedJenkinsAttributes.JENKINS_STEP_NAME,
                                ExtendedJenkinsAttributes.AGENT); // FIXME verify it's the right semantic and value
            }
            if (semConvStability.emitOtelCicdSemConv()) {
                agentSpanBuilder
                        .setAttribute(CicdIncubatingAttributes.CICD_PIPELINE_TASK_RUN_ID, stepStartNode.getId())
                        .setAttribute(CicdIncubatingAttributes.CICD_PIPELINE_TASK_TYPE, stepType)
                        .setAttribute(
                                CicdIncubatingAttributes.CICD_PIPELINE_TASK_NAME,
                                ExtendedJenkinsAttributes.AGENT); // FIXME verify it's the right semantic and value
            }
            if (agentLabel != null) {
                agentSpanBuilder.setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_AGENT_LABEL, agentLabel);
            }
            Span agentSpan = agentSpanBuilder.startSpan();

            LOGGER.log(
                    Level.FINE,
                    () -> run.getFullDisplayName() + " - > " + ExtendedJenkinsAttributes.AGENT + "(" + agentLabel
                            + ") - begin " + OtelUtils.toDebugString(agentSpan));

            getTracerService().putAgentSpan(run, agentSpan, stepStartNode);

            try (Scope allocateAgentSpanScope = agentSpan.makeCurrent()) {
                SpanBuilder allocateAgentSpanBuilder = getTracer()
                        .spanBuilder(ExtendedJenkinsAttributes.AGENT_ALLOCATION_UI)
                        .setParent(Context.current())
                        .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_ID, stepStartNode.getId())
                        .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_PLUGIN_NAME, stepPlugin.getName())
                        .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_PLUGIN_VERSION, stepPlugin.getVersion());
                if (semConvStability.emitLegacyCicdSemConv()) {
                    allocateAgentSpanBuilder
                            .setAttribute(
                                    ExtendedJenkinsAttributes.JENKINS_STEP_TYPE,
                                    getStepType(
                                            stepStartNode,
                                            stepStartNode.getDescriptor(),
                                            ExtendedJenkinsAttributes.STEP_NODE))
                            .setAttribute(
                                    ExtendedJenkinsAttributes.JENKINS_STEP_NAME,
                                    ExtendedJenkinsAttributes
                                            .AGENT_ALLOCATE); // FIXME verify it's the right semantic and value
                }
                if (semConvStability.emitOtelCicdSemConv()) {
                    allocateAgentSpanBuilder
                            .setAttribute(CicdIncubatingAttributes.CICD_PIPELINE_TASK_RUN_ID, stepStartNode.getId())
                            .setAttribute(
                                    CicdIncubatingAttributes.CICD_PIPELINE_TASK_TYPE,
                                    getStepType(
                                            stepStartNode,
                                            stepStartNode.getDescriptor(),
                                            ExtendedJenkinsAttributes.STEP_NODE))
                            .setAttribute(
                                    CicdIncubatingAttributes.CICD_PIPELINE_TASK_NAME,
                                    ExtendedJenkinsAttributes
                                            .AGENT_ALLOCATE); // FIXME verify it's the right semantic and value
                }
                if (agentLabel != null) {
                    allocateAgentSpanBuilder.setAttribute(
                            ExtendedJenkinsAttributes.JENKINS_STEP_AGENT_LABEL, agentLabel);
                }
                Span allocateAgentSpan = allocateAgentSpanBuilder.startSpan();

                LOGGER.log(
                        Level.FINE,
                        () -> run.getFullDisplayName() + " - > " + ExtendedJenkinsAttributes.AGENT_ALLOCATE + "("
                                + agentLabel + ") - begin " + OtelUtils.toDebugString(allocateAgentSpan));

                getTracerService().putAgentSpan(run, allocateAgentSpan, stepStartNode);
            }
        }
    }

    @Override
    public void onAfterStartNodeStep(
            @NonNull StepStartNode stepStartNode, @Nullable String nodeLabel, @NonNull WorkflowRun run) {
        // end the JenkinsOtelSemanticAttributes.AGENT_ALLOCATE span
        endCurrentSpan(stepStartNode, run, null);
    }

    @Override
    public void onStartStageStep(
            @NonNull StepStartNode stepStartNode, @NonNull String stageName, @NonNull WorkflowRun run) {
        try (Scope ignored = setupContext(run, stepStartNode)) {
            verifyNotNull(ignored, "%s - No span found for node %s", run, stepStartNode);
            String spanStageName = "Stage: " + stageName;

            String stepType = getStepType(stepStartNode, stepStartNode.getDescriptor(), "stage");
            JenkinsOpenTelemetryPluginConfiguration.StepPlugin stepPlugin =
                    JenkinsOpenTelemetryPluginConfiguration.get().findStepPluginOrDefault(stepType, stepStartNode);

            Span stageSpan = getTracer()
                    .spanBuilder(spanStageName)
                    .setParent(Context.current())
                    .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_ID, stepStartNode.getId())
                    .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_PLUGIN_NAME, stepPlugin.getName())
                    .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_PLUGIN_VERSION, stepPlugin.getVersion())
                    .startSpan();
            if (semConvStability.emitLegacyCicdSemConv()) {
                stageSpan
                        .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_TYPE, stepType)
                        .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_NAME, stageName);
            }
            if (semConvStability.emitOtelCicdSemConv()) {
                stageSpan
                        .setAttribute(CicdIncubatingAttributes.CICD_PIPELINE_TASK_RUN_ID, stepStartNode.getId())
                        .setAttribute(CicdIncubatingAttributes.CICD_PIPELINE_TASK_TYPE, stepType)
                        .setAttribute(CicdIncubatingAttributes.CICD_PIPELINE_TASK_NAME, stageName);
            }
            LOGGER.log(
                    Level.FINE,
                    () -> run.getFullDisplayName() + " - > stage(" + stageName + ") - begin "
                            + OtelUtils.toDebugString(stageSpan));

            getTracerService().putSpan(run, stageSpan, stepStartNode);
        }
    }

    @Override
    public void onEndNodeStep(
            @NonNull StepEndNode node, @NonNull String nodeName, FlowNode nextNode, @NonNull WorkflowRun run) {
        StepStartNode nodeStartNode = node.getStartNode();
        GenericStatus nodeStatus = StatusAndTiming.computeChunkStatus2(run, null, nodeStartNode, node, nextNode);
        endCurrentSpan(node, run, nodeStatus);
    }

    @Override
    public void onEndStageStep(
            @NonNull StepEndNode node, @NonNull String stageName, FlowNode nextNode, @NonNull WorkflowRun run) {
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
            LOGGER.log(
                    Level.FINE,
                    () -> run.getFullDisplayName() + " - don't create span for step '" + node.getDisplayFunctionName()
                            + "'");
            return;
        }
        Scope encapsulatingNodeScope = setupContext(run, node);

        verifyNotNull(encapsulatingNodeScope, "%s - No span found for node %s", run, node);

        String principal =
                Objects.toString(node.getExecution().getAuthentication2().getPrincipal(), "#null#");

        StepHandler stepHandler = getStepHandlers().stream()
                .filter(sh -> sh.canCreateSpanBuilder(node, run))
                .findFirst()
                .orElseThrow((Supplier<RuntimeException>) () -> new IllegalStateException(
                        "No StepHandler found for node " + node.getClass() + " - " + node + " on " + run));
        SpanBuilder spanBuilder = stepHandler.createSpanBuilder(node, run, getTracer());

        String stepType = getStepType(node, node.getDescriptor(), ExtendedJenkinsAttributes.STEP_NAME);
        JenkinsOpenTelemetryPluginConfiguration.StepPlugin stepPlugin =
                JenkinsOpenTelemetryPluginConfiguration.get().findStepPluginOrDefault(stepType, node);

        spanBuilder
                .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_ID, node.getId())
                .setAttribute(ExtendedJenkinsAttributes.CI_PIPELINE_RUN_USER, principal)
                .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_PLUGIN_NAME, stepPlugin.getName())
                .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_PLUGIN_VERSION, stepPlugin.getVersion());
        if (semConvStability.emitLegacyCicdSemConv()) {
            spanBuilder
                    .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_TYPE, stepType)
                    .setAttribute(
                            ExtendedJenkinsAttributes.JENKINS_STEP_NAME,
                            getStepName(node, ExtendedJenkinsAttributes.STEP_NAME));
        }
        if (semConvStability.emitOtelCicdSemConv()) {
            spanBuilder
                    .setAttribute(CicdIncubatingAttributes.CICD_PIPELINE_TASK_RUN_ID, node.getId())
                    .setAttribute(CicdIncubatingAttributes.CICD_PIPELINE_TASK_TYPE, stepType)
                    .setAttribute(
                            CicdIncubatingAttributes.CICD_PIPELINE_TASK_NAME,
                            getStepName(node, ExtendedJenkinsAttributes.STEP_NAME));
        }

        Span atomicStepSpan = spanBuilder.startSpan();
        LOGGER.log(
                Level.FINE,
                () -> run.getFullDisplayName() + " - > " + node.getDisplayFunctionName() + " - begin "
                        + OtelUtils.toDebugString(atomicStepSpan));
        Scope atomicStepScope = atomicStepSpan.makeCurrent();
        stepHandler.afterSpanCreated(node, run);

        getTracerService()
                .putSpanAndScopes(run, atomicStepSpan, node, Arrays.asList(encapsulatingNodeScope, atomicStepScope));
    }

    @Override
    public void onAfterAtomicStep(@NonNull StepAtomNode node, FlowNode nextNode, @NonNull WorkflowRun run) {
        if (isIgnoredStep(node.getDescriptor())) {
            LOGGER.log(
                    Level.FINE,
                    () -> run.getFullDisplayName() + " - don't end span for step '" + node.getDisplayFunctionName()
                            + "'");
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
                || getIgnoredStepNames().contains(stepFunctionName);
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
            Descriptor<? extends Describable<?>> d =
                    SymbolLookup.get().findDescriptor(Describable.class, describable.getSymbol());
            return d.getDisplayName();
        }
        return stepDescriptor.getDisplayName();
    }

    private String getStepName(@NonNull StepStartNode node, @NonNull String name) {
        StepDescriptor stepDescriptor = node.getDescriptor();
        if (stepDescriptor == null) {
            return name;
        }
        UninstantiatedDescribable describable = getUninstantiatedDescribableOrNull(node, stepDescriptor);
        if (describable != null) {
            Descriptor<? extends Describable<?>> d =
                    SymbolLookup.get().findDescriptor(Describable.class, describable.getSymbol());
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
    private UninstantiatedDescribable getUninstantiatedDescribableOrNull(
            @NonNull FlowNode node, @Nullable StepDescriptor stepDescriptor) {
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
    public void onStartParallelStepBranch(
            @NonNull StepStartNode stepStartNode, @NonNull String branchName, @NonNull WorkflowRun run) {
        try (Scope ignored = setupContext(run, stepStartNode)) {
            verifyNotNull(ignored, "%s - No span found for node %s", run, stepStartNode);

            String stepType = getStepType(stepStartNode, stepStartNode.getDescriptor(), "branch");
            JenkinsOpenTelemetryPluginConfiguration.StepPlugin stepPlugin =
                    JenkinsOpenTelemetryPluginConfiguration.get().findStepPluginOrDefault(stepType, stepStartNode);

            Span atomicStepSpan = getTracer()
                    .spanBuilder("Parallel branch: " + branchName)
                    .setParent(Context.current())
                    .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_ID, stepStartNode.getId())
                    .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_PLUGIN_NAME, stepPlugin.getName())
                    .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_PLUGIN_VERSION, stepPlugin.getVersion())
                    .startSpan();
            if (semConvStability.emitLegacyCicdSemConv()) {
                atomicStepSpan
                        .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_TYPE, stepType)
                        .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_NAME, branchName);
            }
            if (semConvStability.emitOtelCicdSemConv()) {
                atomicStepSpan
                        .setAttribute(CicdIncubatingAttributes.CICD_PIPELINE_TASK_RUN_ID, stepStartNode.getId())
                        .setAttribute(CicdIncubatingAttributes.CICD_PIPELINE_TASK_TYPE, stepType)
                        .setAttribute(CicdIncubatingAttributes.CICD_PIPELINE_TASK_NAME, branchName);
            }
            LOGGER.log(
                    Level.FINE,
                    () -> run.getFullDisplayName() + " - > parallel branch(" + branchName + ") - begin "
                            + OtelUtils.toDebugString(atomicStepSpan));

            getTracerService().putSpan(run, atomicStepSpan, stepStartNode);
        }
    }

    @Override
    public void onEndParallelStepBranch(
            @NonNull StepEndNode node, @NonNull String branchName, FlowNode nextNode, @NonNull WorkflowRun run) {
        StepStartNode parallelStartNode = node.getStartNode();
        GenericStatus parallelStatus =
                StatusAndTiming.computeChunkStatus2(run, null, parallelStartNode, node, nextNode);
        endCurrentSpan(node, run, parallelStatus);
    }

    private void endCurrentSpan(FlowNode node, WorkflowRun run, GenericStatus status) {
        endCurrentSpan(node, run, status, -1);
    }

    /**
     * Ends a span that the "block steps" setting adds: the span of a block step, or of an invocation of its body. The
     * span ends at the time of the end node (it may be ended later, when the next node arrives). Every such span ends
     * here, so no handler can skip the rule.
     */
    private void endBlockStepSpan(@NonNull StepEndNode endNode, @NonNull WorkflowRun run, GenericStatus status) {
        endCurrentSpan(endNode, run, status, TimingAction.getStartTime(endNode));
    }

    /**
     * Ends the span of the start node of the given end node, or of the given atomic node, with its status and error.
     * Meant to be called when the next node after the end node has arrived, because the error of a failed body is
     * added to the end node after the node is created.
     *
     * @param node          the end node, or the atomic node, whose span ends
     * @param run           the run of the node
     * @param status        the status of the chunk of the node, or {@code null} to take it from the error of the node
     * @param endTimeMillis end time of the span in milliseconds since the epoch, for example the time of the end
     *                      node ({@link TimingAction#getStartTime(FlowNode)}). Zero or less: now.
     */
    private void endCurrentSpan(FlowNode node, WorkflowRun run, GenericStatus status, long endTimeMillis) {
        try (Scope ignored = setupContext(run, node)) {
            verifyNotNull(ignored, "%s - No span found for node %s", run, node);

            Span span = getTracerService().getSpan(run, node);

            ErrorAction errorAction = node.getError();
            if (errorAction == null) {
                if (status == null) status = GenericStatus.SUCCESS;
                span.setStatus(StatusCode.OK);
            } else {
                Throwable throwable = errorAction.getError();
                if (throwable instanceof FlowInterruptedException interruptedException) {
                    List<CauseOfInterruption> causesOfInterruption = interruptedException.getCauses();

                    if (status == null) status = GenericStatus.fromResult(interruptedException.getResult());

                    List<String> causeDescriptions = causesOfInterruption.stream()
                            .map(cause -> cause.getClass().getSimpleName() + ": " + cause.getShortDescription())
                            .collect(Collectors.toList());
                    span.setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_INTERRUPTION_CAUSES, causeDescriptions);

                    boolean suppressSpanStatusCodeError = false;
                    for (CauseOfInterruption causeOfInterruption : causesOfInterruption) {
                        if (statusUnsetCausesOfInterruption.contains(
                                causeOfInterruption.getClass().getName())) {
                            suppressSpanStatusCodeError = true;
                            break;
                        }
                    }
                    if (suppressSpanStatusCodeError) {
                        // status.description can't be set for status `unset` as specified by
                        // https://github.com/open-telemetry/opentelemetry-specification/blob/v1.43.0/specification/trace/api.md#set-status
                        span.setStatus(StatusCode.UNSET);
                    } else {
                        span.recordException(throwable);
                        String statusDescription =
                                throwable.getClass().getSimpleName() + ": " + String.join(", ", causeDescriptions);
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
                if (semConvStability.emitLegacyCicdSemConv()) {
                    span.setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_RESULT, status.toString());
                }
                if (semConvStability.emitOtelCicdSemConv()) {
                    span.setAttribute(
                            CicdIncubatingAttributes.CICD_PIPELINE_TASK_RUN_RESULT,
                            CicdMetrics.fromJenkinsGenericResultToOtelCicdPipelineResult(status));
                }
            }

            if (endTimeMillis > 0) {
                span.end(endTimeMillis, TimeUnit.MILLISECONDS);
            } else {
                span.end();
            }
            LOGGER.log(
                    Level.FINE,
                    () -> run.getFullDisplayName() + " - < " + node.getDisplayFunctionName() + " - end "
                            + OtelUtils.toDebugString(span));

            getTracerService().removePipelineStepSpanAndCloseAssociatedScopes(run, node, span);
        }
    }

    @Override
    public void onStartWithNewSpanStep(@NonNull StepStartNode stepStartNode, @NonNull WorkflowRun run) {
        try (Scope ignored = setupContext(run, stepStartNode)) {
            verifyNotNull(ignored, "%s - No span found for node %s", run, stepStartNode);

            String stepName = getStepName(stepStartNode, "withNewSpan");
            String stepType = getStepType(stepStartNode, stepStartNode.getDescriptor(), "step");
            JenkinsOpenTelemetryPluginConfiguration.StepPlugin stepPlugin =
                    JenkinsOpenTelemetryPluginConfiguration.get().findStepPluginOrDefault(stepType, stepStartNode);

            // Get the arguments.
            final Map<String, Object> arguments = ArgumentsAction.getFilteredArguments(stepStartNode);
            // Argument 'label'.
            final String spanLabelArgument = (String) arguments.getOrDefault("label", stepName);
            final String spanLabel = Strings.isNullOrEmpty(spanLabelArgument) ? stepName : spanLabelArgument;
            SpanBuilder spanBuilder = getTracer()
                    .spanBuilder(spanLabel)
                    .setParent(Context.current())
                    .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_ID, stepStartNode.getId())
                    .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_PLUGIN_NAME, stepPlugin.getName())
                    .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_PLUGIN_VERSION, stepPlugin.getVersion());
            if (semConvStability.emitLegacyCicdSemConv()) {
                spanBuilder
                        .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_TYPE, stepType)
                        .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_NAME, stepName);
            }
            if (semConvStability.emitOtelCicdSemConv()) {
                spanBuilder
                        .setAttribute(CicdIncubatingAttributes.CICD_PIPELINE_TASK_RUN_ID, stepStartNode.getId())
                        .setAttribute(CicdIncubatingAttributes.CICD_PIPELINE_TASK_TYPE, stepType)
                        .setAttribute(CicdIncubatingAttributes.CICD_PIPELINE_TASK_NAME, stepName);
            }

            // Populate the attributes if any 'attributes' argument was passed to the 'withNewSpan' step.
            try {
                Object attributesObj = arguments.getOrDefault("attributes", Collections.emptyList());
                if (attributesObj instanceof List<?>) {
                    // Filter the list items and cast to SpanAttribute.
                    List<SpanAttribute> attributes = ((List<?>) attributesObj)
                            .stream()
                                    .filter(item -> item instanceof SpanAttribute)
                                    .map(item -> (SpanAttribute) item)
                                    .toList();

                    for (SpanAttribute attribute : attributes) {
                        // Set the attributeType in case it's not there.
                        attributes.forEach(SpanAttribute::setDefaultType);
                        // attributeKey is null, call convert() to set the appropriate key value
                        // and convert the attribute value.
                        attribute.convert();
                        spanBuilder.setAttribute(attribute.getAttributeKey(), attribute.getConvertedValue());
                    }
                } else {
                    LOGGER.log(
                            Level.WARNING,
                            "Attributes are in an unexpected format: "
                                    + attributesObj.getClass().getSimpleName());
                }
            } catch (ClassCastException cce) {
                LOGGER.log(
                        Level.WARNING,
                        run.getFullDisplayName() + " failure to gather the attributes for the 'withNewSpan' step.",
                        cce);
            }

            Span newSpan = spanBuilder.startSpan();
            LOGGER.log(
                    Level.FINE,
                    () -> run.getFullDisplayName() + " - > " + stepStartNode.getDisplayFunctionName() + " - begin "
                            + OtelUtils.toDebugString(newSpan));
            getTracerService().putSpan(run, newSpan, stepStartNode);
        }
    }

    @Override
    public void onEndWithNewSpanStep(@NonNull StepEndNode node, FlowNode nextNode, @NonNull WorkflowRun run) {
        StepStartNode nodeStartNode = node.getStartNode();
        GenericStatus nodeStatus = StatusAndTiming.computeChunkStatus2(run, null, nodeStartNode, node, nextNode);
        endCurrentSpan(node, run, nodeStatus);
    }

    @Override
    public void onStartBlockStep(@NonNull StepStartNode stepStartNode, @NonNull WorkflowRun run) {
        StepDescriptor descriptor = stepStartNode.getDescriptor();
        if (!isTracedBlockStep(descriptor)) {
            return;
        }
        String functionName = descriptor.getFunctionName();
        try (Scope ignored = setupContext(run, stepStartNode)) {
            verifyNotNull(ignored, "%s - No span found for node %s", run, stepStartNode);
            String stepType = getStepType(stepStartNode, descriptor, functionName);
            String stepName = getStepName(stepStartNode, functionName);
            JenkinsOpenTelemetryPluginConfiguration.StepPlugin stepPlugin =
                    JenkinsOpenTelemetryPluginConfiguration.get().findStepPluginOrDefault(stepType, stepStartNode);

            SpanBuilder spanBuilder = getTracer()
                    .spanBuilder(functionName)
                    .setParent(Context.current())
                    .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_ID, stepStartNode.getId())
                    .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_PLUGIN_NAME, stepPlugin.getName())
                    .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_PLUGIN_VERSION, stepPlugin.getVersion());
            if (semConvStability.emitLegacyCicdSemConv()) {
                spanBuilder
                        .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_TYPE, stepType)
                        .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_NAME, stepName);
            }
            if (semConvStability.emitOtelCicdSemConv()) {
                spanBuilder
                        .setAttribute(CicdIncubatingAttributes.CICD_PIPELINE_TASK_RUN_ID, stepStartNode.getId())
                        .setAttribute(CicdIncubatingAttributes.CICD_PIPELINE_TASK_TYPE, stepType)
                        .setAttribute(CicdIncubatingAttributes.CICD_PIPELINE_TASK_NAME, stepName);
            }
            Span blockSpan = spanBuilder.startSpan();
            LOGGER.log(
                    Level.FINE,
                    () -> run.getFullDisplayName() + " - > " + functionName + " - begin "
                            + OtelUtils.toDebugString(blockSpan));
            getTracerService().putSpan(run, blockSpan, stepStartNode);
            callBlockStepHandlers(
                    functionName,
                    "onBlockStepStart",
                    handler -> handler.onBlockStepStart(blockSpan, stepStartNode, run));
        }
    }

    @Override
    public void onEndBlockStep(
            @NonNull StepEndNode stepEndNode, @CheckForNull FlowNode nextNode, @NonNull WorkflowRun run) {
        StepStartNode stepStartNode = stepEndNode.getStartNode();
        StepDescriptor descriptor = stepStartNode.getDescriptor();
        // Not decided by the settings: a step that was removed from them while its block ran still ends its span.
        Span blockSpan = findLiveSpan(stepStartNode);
        if (descriptor == null
                || STEPS_WITH_THEIR_OWN_SPAN.contains(descriptor.getFunctionName())
                || blockSpan == null) {
            // No block span (the step has none, or the controller restarted): the span of the enclosing block must
            // not be ended in its place.
            LOGGER.log(
                    Level.FINE,
                    () -> run.getFullDisplayName() + " - no live span for the end of "
                            + stepStartNode.getDisplayName());
            return;
        }
        String functionName = descriptor.getFunctionName();
        callBlockStepHandlers(
                functionName,
                "onBlockStepEnd",
                handler -> handler.onBlockStepEnd(blockSpan, stepEndNode, nextNode, run));
        GenericStatus status = StatusAndTiming.computeChunkStatus2(run, null, stepStartNode, stepEndNode, nextNode);
        endBlockStepSpan(stepEndNode, run, status);
    }

    @Override
    public void onEndBlockStepWithoutEndNode(
            @NonNull StepStartNode stepStartNode, @NonNull FlowNode nextNode, @NonNull WorkflowRun run) {
        StepDescriptor descriptor = stepStartNode.getDescriptor();
        LiveSpan liveSpan = findLiveSpanOfNode(stepStartNode);
        if (descriptor == null
                || STEPS_WITH_THEIR_OWN_SPAN.contains(descriptor.getFunctionName())
                || liveSpan == null) {
            return;
        }
        LOGGER.log(
                Level.FINE,
                () -> run.getFullDisplayName() + " - " + descriptor.getFunctionName()
                        + " completed without an end node, ending its span");
        liveSpan.span().setStatus(StatusCode.OK);
        liveSpan.span().end(TimingAction.getStartTime(nextNode), TimeUnit.MILLISECONDS);
        liveSpan.action().purgeSpanAndCloseAssociatedScopes();
    }

    @Override
    public void onStartBlockBody(@NonNull StepStartNode bodyStartNode, @NonNull WorkflowRun run) {
        LiveBlock block = findLiveBlock(bodyStartNode);
        if (block == null) {
            return;
        }
        StepStartNode outerStartNode = block.outerStartNode();
        Span blockSpan = block.span();
        String functionName = block.functionName();
        for (BlockStepHandler handler : BlockStepHandler.all()) {
            if (!handler.canHandle(functionName)) {
                continue;
            }
            Optional<BlockStepHandler.BodySpan> bodySpan;
            try {
                bodySpan = handler.bodySpan(outerStartNode, bodyStartNode);
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, e, () -> "Exception invoking `bodySpan` on " + handler);
                continue;
            }
            if (bodySpan.isPresent()) {
                startBodySpan(bodySpan.get(), blockSpan, bodyStartNode, run);
                // One span per invocation of the body
                break;
            }
        }
        callBlockStepHandlers(
                functionName,
                "onBlockBodyStart",
                handler -> handler.onBlockBodyStart(blockSpan, outerStartNode, bodyStartNode, run));
    }

    private void startBodySpan(
            BlockStepHandler.BodySpan bodySpan, Span blockSpan, StepStartNode bodyStartNode, WorkflowRun run) {
        Span span = getTracer()
                .spanBuilder(bodySpan.name())
                .setParent(Context.current().with(blockSpan))
                .setAllAttributes(bodySpan.attributes())
                .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_ID, bodyStartNode.getId())
                .startSpan();
        LOGGER.log(
                Level.FINE,
                () -> run.getFullDisplayName() + " - > " + bodySpan.name() + " - begin "
                        + OtelUtils.toDebugString(span));
        getTracerService().putSpan(run, span, bodyStartNode);
    }

    @Override
    public void onEndBlockBody(
            @NonNull StepEndNode bodyEndNode, @CheckForNull FlowNode nextNode, @NonNull WorkflowRun run) {
        StepStartNode bodyStartNode = bodyEndNode.getStartNode();
        LiveBlock block = findLiveBlock(bodyStartNode);
        if (block == null) {
            return;
        }
        callBlockStepHandlers(
                block.functionName(), "onBlockBodyEnd", handler -> handler.onBlockBodyEnd(bodyEndNode, nextNode, run));
        // No span for this invocation (none asked for, or lost in a restart): the span of the step must not end here
        if (findLiveSpan(bodyStartNode) != null) {
            endBlockStepSpan(bodyEndNode, run, null);
        }
    }

    /** A handler that throws must not stop the span from ending, nor the other handlers. */
    private void callBlockStepHandlers(String functionName, String callback, Consumer<BlockStepHandler> call) {
        for (BlockStepHandler handler : BlockStepHandler.all()) {
            if (!handler.canHandle(functionName)) {
                continue;
            }
            try {
                call.accept(handler);
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, e, () -> "Exception invoking `" + callback + "` on " + handler);
            }
        }
    }

    /** A block step whose span has not ended. */
    private record LiveBlock(StepStartNode outerStartNode, String functionName, Span span) {}

    /** A span on a flow node that has not ended, with the action that holds it. */
    private record LiveSpan(FlowNodeMonitoringAction action, Span span) {}

    /**
     * The body callbacks run for the body of every block step, listed or not, so this does not walk the enclosing
     * blocks: the innermost enclosing block of a body start node is the outer start node of its step, and it is
     * found by its id.
     *
     * @return the block that the body start node belongs to, if its block span has not ended, else {@code null}. Like
     *         the end of the block, the body callbacks follow the span, not the settings of the moment.
     */
    @CheckForNull
    private static LiveBlock findLiveBlock(@NonNull StepStartNode bodyStartNode) {
        StepDescriptor descriptor = bodyStartNode.getDescriptor();
        if (descriptor == null || STEPS_WITH_THEIR_OWN_SPAN.contains(descriptor.getFunctionName())) {
            return null;
        }
        FlowNode enclosing;
        try {
            String enclosingId = bodyStartNode.getEnclosingId();
            enclosing =
                    enclosingId == null ? null : bodyStartNode.getExecution().getNode(enclosingId);
        } catch (IOException e) {
            LOGGER.log(Level.FINE, e, () -> "Cannot read the block of " + bodyStartNode.getDisplayName());
            return null;
        }
        if (enclosing instanceof StepStartNode outerStartNode && descriptor.equals(outerStartNode.getDescriptor())) {
            Span span = findLiveSpan(outerStartNode);
            return span == null ? null : new LiveBlock(outerStartNode, descriptor.getFunctionName(), span);
        }
        return null;
    }

    /** @return the last span put on this node that has not ended, or {@code null} */
    @CheckForNull
    private static Span findLiveSpan(@NonNull FlowNode node) {
        LiveSpan liveSpan = findLiveSpanOfNode(node);
        return liveSpan == null ? null : liveSpan.span();
    }

    /** @return the last span put on this node that has not ended, with its action, or {@code null} */
    @CheckForNull
    private static LiveSpan findLiveSpanOfNode(@NonNull FlowNode node) {
        List<FlowNodeMonitoringAction> actions = node.getActions(FlowNodeMonitoringAction.class);
        for (int i = actions.size() - 1; i >= 0; i--) {
            FlowNodeMonitoringAction action = actions.get(i);
            Span span = action.getSpan();
            if (!action.hasEnded() && span != null) {
                return new LiveSpan(action, span);
            }
        }
        return null;
    }

    /**
     * A block step gets a span if its name is in the "block steps" setting, it is not in the "ignored steps"
     * setting, and the plugin does not already handle it ({@code node}, {@code stage}, {@code parallel},
     * {@code withNewSpan}, and the steps that set span attributes).
     */
    private boolean isTracedBlockStep(@Nullable StepDescriptor descriptor) {
        if (descriptor == null || isIgnoredStep(descriptor)) {
            return false;
        }
        String functionName = descriptor.getFunctionName();
        return !STEPS_WITH_THEIR_OWN_SPAN.contains(functionName)
                && getBlockStepNames().contains(functionName);
    }

    private Set<String> getIgnoredStepNames() {
        ignoredSteps = refresh(
                ignoredSteps,
                Objects.toString(JenkinsOpenTelemetryPluginConfiguration.get().getIgnoredSteps(), ""));
        return ignoredSteps.names();
    }

    private Set<String> getBlockStepNames() {
        blockSteps = refresh(
                blockSteps, JenkinsOpenTelemetryPluginConfiguration.get().getBlockSteps());
        return blockSteps.names();
    }

    /** The settings are read at each use so that a change applies to the next step without a restart. */
    private static ParsedStepNames refresh(ParsedStepNames parsed, String commaSeparatedNames) {
        if (Objects.equals(parsed.source(), commaSeparatedNames)) {
            return parsed;
        }
        return new ParsedStepNames(commaSeparatedNames, Set.copyOf(Arrays.asList(commaSeparatedNames.split(","))));
    }

    @Override
    public void notifyOfNewStep(@NonNull Step step, @NonNull StepContext context) {
        try {
            WorkflowRun run = context.get(WorkflowRun.class);
            FlowNode node = context.get(FlowNode.class);
            Computer computer = context.get(Computer.class);
            if (computer == null || node == null || run == null) {
                LOGGER.log(
                        Level.FINER,
                        () -> "No run, flowNode or computer, skip. Run:" + run + ", flowNode: " + node + ", computer:"
                                + computer);
                return;
            }
            if (computer.getAction(OpenTelemetryAttributesAction.class) == null) {
                LOGGER.log(
                        Level.WARNING,
                        "Unexpected missing " + OpenTelemetryAttributesAction.class + " on " + computer
                                + ", adding fallback");
                String hostName = computer.getHostName();
                OpenTelemetryAttributesAction openTelemetryAttributesAction = new OpenTelemetryAttributesAction();
                var actionAttributes = openTelemetryAttributesAction.getAttributes();
                if (hostName != null) {
                    // getHostName() returns null if the master cannot find the host name, e.g. due to network settings.
                    // @see hudson.model.Computer#getHostName()
                    actionAttributes.put(HostIncubatingAttributes.HOST_NAME, hostName);
                }
                if (semConvStability.emitLegacyCicdSemConv()) {
                    actionAttributes.put(ExtendedJenkinsAttributes.JENKINS_COMPUTER_NAME, computer.getName());
                }
                if (semConvStability.emitOtelCicdSemConv()) {
                    actionAttributes.put(CicdIncubatingAttributes.CICD_WORKER_ID, computer.getName());
                    actionAttributes.put(CicdIncubatingAttributes.CICD_WORKER_NAME, computer.getName());
                }
                computer.addAction(openTelemetryAttributesAction);
            }
            OpenTelemetryAttributesAction otelComputerAttributesAction =
                    computer.getAction(OpenTelemetryAttributesAction.class);
            OpenTelemetryAttributesAction otelChildAttributesAction = context.get(OpenTelemetryAttributesAction.class);

            try (Scope ignored = setupContext(run, node)) {
                Span currentSpan = Span.current();
                LOGGER.log(
                        Level.FINE,
                        () -> "Add resource attributes to span " + OtelUtils.toDebugString(currentSpan) + " - "
                                + otelComputerAttributesAction);
                setAttributesToSpan(currentSpan, otelComputerAttributesAction);

                LOGGER.log(
                        Level.FINE,
                        () -> "Add attributes to child span " + OtelUtils.toDebugString(currentSpan) + " - "
                                + otelChildAttributesAction);
                setAttributesToSpan(currentSpan, otelChildAttributesAction);
            }
        } catch (IOException | InterruptedException | RuntimeException e) {
            LOGGER.log(Level.WARNING, "Exception processing " + step + " - " + context, e);
        }
    }

    private void setAttributesToSpan(@NonNull Span span, OpenTelemetryAttributesAction openTelemetryAttributesAction) {
        if (openTelemetryAttributesAction == null) {
            return;
        }

        // If the list is empty, ignore this check.
        if (!openTelemetryAttributesAction.inheritanceAllowedSpanIdListIsEmpty()
                && !openTelemetryAttributesAction.isSpanIdAllowedToInheritAttributes(
                        span.getSpanContext().getSpanId())) {
            // If the list isn't empty, then the attributes shouldn't be set on children spans.
            // Attributes should only be set on Ids from the list.
            // If there are Ids on the list but the provided Id isn't part of them,
            // don't set attributes on the span.
            return;
        }

        if (!openTelemetryAttributesAction.isNotYetAppliedToSpan(span)) {
            // Do not reapply attributes, if previously applied.
            // This is important for overriding of attributes to work in an intuitive manner.
            return;
        }
        for (Map.Entry<AttributeKey<?>, Object> entry :
                openTelemetryAttributesAction.getAttributes().entrySet()) {
            AttributeKey<?> attributeKey = entry.getKey();
            Object value = verifyNotNull(entry.getValue());
            span.setAttribute((AttributeKey<? super Object>) attributeKey, value);
        }
    }

    /**
     * @return {@code null} if no {@link Span} has been created for the {@link Run} of the given {@link FlowNode}
     */
    @NonNull
    @MustBeClosed
    protected Scope setupContext(WorkflowRun run, @NonNull FlowNode node) {
        verifyNotNull(run, "%s No run found for node %s", run, node);
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
}
