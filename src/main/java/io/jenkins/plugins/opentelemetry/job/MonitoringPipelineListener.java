/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import static com.google.common.base.Verify.verifyNotNull;

import com.google.common.base.Strings;
import com.google.errorprone.annotations.MustBeClosed;
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
import io.jenkins.plugins.opentelemetry.job.jenkins.AbstractPipelineListener;
import io.jenkins.plugins.opentelemetry.job.step.SetSpanAttributesStep;
import io.jenkins.plugins.opentelemetry.job.step.SpanAttribute;
import io.jenkins.plugins.opentelemetry.job.step.StepHandler;
import io.jenkins.plugins.opentelemetry.job.step.WithSpanAttributeStep;
import io.jenkins.plugins.opentelemetry.job.step.WithSpanAttributesStep;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.incubating.HostIncubatingAttributes;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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
import javax.annotation.PostConstruct;
import javax.inject.Inject;
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

        final JenkinsOpenTelemetryPluginConfiguration jenkinsOpenTelemetryPluginConfiguration =
                JenkinsOpenTelemetryPluginConfiguration.get();
        this.ignoredSteps = new HashSet<>(Arrays.asList(
                jenkinsOpenTelemetryPluginConfiguration.getIgnoredSteps().split(",")));
        this.statusUnsetCausesOfInterruption =
                new HashSet<>(jenkinsOpenTelemetryPluginConfiguration.getStatusUnsetCausesOfInterruption());
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
                    .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_TYPE, stepType)
                    .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_ID, stepStartNode.getId())
                    .setAttribute(
                            ExtendedJenkinsAttributes.JENKINS_STEP_NAME,
                            ExtendedJenkinsAttributes.AGENT) // FIXME verify it's the right semantic and value
                    .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_PLUGIN_NAME, stepPlugin.getName())
                    .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_PLUGIN_VERSION, stepPlugin.getVersion());
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
                        .setAttribute(
                                ExtendedJenkinsAttributes.JENKINS_STEP_TYPE,
                                getStepType(
                                        stepStartNode,
                                        stepStartNode.getDescriptor(),
                                        ExtendedJenkinsAttributes.STEP_NODE))
                        .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_ID, stepStartNode.getId())
                        .setAttribute(
                                ExtendedJenkinsAttributes.JENKINS_STEP_NAME,
                                ExtendedJenkinsAttributes
                                        .AGENT_ALLOCATE) // FIXME verify it's the right semantic and value
                        .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_PLUGIN_NAME, stepPlugin.getName())
                        .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_PLUGIN_VERSION, stepPlugin.getVersion());
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
                    .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_TYPE, stepType)
                    .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_ID, stepStartNode.getId())
                    .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_NAME, stageName)
                    .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_PLUGIN_NAME, stepPlugin.getName())
                    .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_PLUGIN_VERSION, stepPlugin.getVersion())
                    .startSpan();
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
                .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_TYPE, stepType)
                .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_ID, node.getId())
                .setAttribute(
                        ExtendedJenkinsAttributes.JENKINS_STEP_NAME,
                        getStepName(node, ExtendedJenkinsAttributes.STEP_NAME))
                .setAttribute(ExtendedJenkinsAttributes.CI_PIPELINE_RUN_USER, principal)
                .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_PLUGIN_NAME, stepPlugin.getName())
                .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_PLUGIN_VERSION, stepPlugin.getVersion());

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
                    .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_TYPE, stepType)
                    .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_ID, stepStartNode.getId())
                    .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_NAME, branchName)
                    .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_PLUGIN_NAME, stepPlugin.getName())
                    .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_PLUGIN_VERSION, stepPlugin.getVersion())
                    .startSpan();
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
                span.setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_RESULT, status.toString());
            }

            span.end();
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
                    .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_TYPE, stepType)
                    .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_ID, stepStartNode.getId())
                    .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_NAME, stepName)
                    .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_PLUGIN_NAME, stepPlugin.getName())
                    .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_PLUGIN_VERSION, stepPlugin.getVersion());

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
                if (hostName != null) {
                    // getHostName() returns null if the master cannot find the host name, e.g. due to network settings.
                    // @see hudson.model.Computer#getHostName()
                    openTelemetryAttributesAction.getAttributes().put(HostIncubatingAttributes.HOST_NAME, hostName);
                }
                openTelemetryAttributesAction
                        .getAttributes()
                        .put(
                                AttributeKey.stringKey(ExtendedJenkinsAttributes.JENKINS_COMPUTER_NAME.getKey()),
                                computer.getName());
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

        if (!openTelemetryAttributesAction.isNotYetAppliedToSpan(
                span.getSpanContext().getSpanId())) {
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
