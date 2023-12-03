/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.tasks.BuildStep;
import io.jenkins.plugins.opentelemetry.OtelUtils;
import io.jenkins.plugins.opentelemetry.job.action.BuildStepMonitoringAction;
import io.jenkins.plugins.opentelemetry.job.action.FlowNodeMonitoringAction;
import io.jenkins.plugins.opentelemetry.job.action.OtelMonitoringAction;
import io.jenkins.plugins.opentelemetry.job.action.RunPhaseMonitoringAction;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.AtomNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graph.GraphLookupView;
import org.jenkinsci.plugins.workflow.graphanalysis.ForkScanner;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.steps.ExecutorStep;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Extension
public class OtelTraceService {
    private static final Logger LOGGER = Logger.getLogger(OtelTraceService.class.getName());

    @SuppressFBWarnings("MS_SHOULD_BE_FINAL")
    public static boolean STRICT_MODE = false;

    public OtelTraceService() {
    }

    /**
     * Returns the span of the current run phase.
     *
     * @return the span of the current pipeline run phase:
     * {@link JenkinsOtelSemanticAttributes#JENKINS_JOB_SPAN_PHASE_START_NAME},
     * {@link JenkinsOtelSemanticAttributes#JENKINS_JOB_SPAN_PHASE_RUN_NAME},
     * {@link JenkinsOtelSemanticAttributes#JENKINS_JOB_SPAN_PHASE_FINALIZE_NAME},
     */
    public Span getSpan(@NonNull Run run) {
        return ImmutableList.copyOf(run.getActions(RunPhaseMonitoringAction.class))
            .reverse()
            .stream().filter(Predicate.not(RunPhaseMonitoringAction::hasEnded))
            .findFirst()
            .map(RunPhaseMonitoringAction::getSpan)
            .orElse(Span.getInvalid());
    }

    /**
     * Returns top level span of the {@link Run}
     */
    @NonNull
    public Span getPipelineRootSpan(@NonNull Run run) {
        return run.getActions(MonitoringAction.class).stream().findFirst().map(MonitoringAction::getSpan).orElse(Span.getInvalid());
    }

    @NonNull
    public Span getSpan(@NonNull Run run, FlowNode flowNode) {
        Iterable<FlowNode> ancestors = getAncestors(flowNode);
        for (FlowNode currentFlowNode : ancestors) {
            Optional<Span> span = ImmutableList.copyOf(currentFlowNode.getActions(FlowNodeMonitoringAction.class))
                .reverse() // from last to first
                .stream()
                .filter(Predicate.not(FlowNodeMonitoringAction::hasEnded)) // only the non ended spans
                .findFirst().map(FlowNodeMonitoringAction::getSpan);
            if (span.isPresent()) {
                return span.get();
            }
        }

        return getSpan(run);
    }

    @NonNull
    public Span getSpan(@NonNull AbstractBuild build, @NonNull BuildStep buildStep) {
        return ImmutableList.copyOf(build.getActions(BuildStepMonitoringAction.class)).reverse() // from last to first
            .stream()
            .filter(Predicate.not(BuildStepMonitoringAction::hasEnded)) // only the non ended spans
            .findFirst().map(BuildStepMonitoringAction::getSpan)
            .orElseGet(() -> getSpan(build)); // or else get the phase span
    }

    /**
     * Return the chain of enclosing flowNodes including the given flow node. If the given flow node is a step end node,
     * the associated step start node is also added.
     * <p>
     * Example
     * <pre>
     * test-pipeline-with-parallel-step8
     *    |- Phase: Start
     *    |- Phase: Run
     *    |   |- Agent, function: node, name: agent, node.id: 3
     *    |       |- Agent Allocation, function: node, name: agent.allocate, node.id: 3
     *    |       |- Stage: ze-parallel-stage, function: stage, name: ze-parallel-stage, node.id: 6
     *    |           |- Parallel branch: parallelBranch1, function: parallel, name: parallelBranch1, node.id: 10
     *    |           |   |- shell-1, function: sh, name: Shell Script, node.id: 14
     *    |           |- Parallel branch: parallelBranch2, function: parallel, name: parallelBranch2, node.id: 11
     *    |           |   |- shell-2, function: sh, name: Shell Script, node.id: 16
     *    |           |- Parallel branch: parallelBranch3, function: parallel, name: parallelBranch3, node.id: 12
     *    |               |- shell-3, function: sh, name: Shell Script, node.id: 18
     *    |- Phase: Finalise
     * </pre>
     * <p>
     * {@code getAncestors("shell-3/node.id: 18")} will return {@code [
     * "shell-3/node.id: 18",
     * "Parallel branch: parallelBranch3/node.id: 12",
     * "Stage: ze-parallel-stage, node.id: 6",
     * "node / node.id: 3",
     * "Start of Pipeline / node.id: 2" // not visualized above
     * ]}
     * TODO optimize lazing loading the enclosing blocks using {@link GraphLookupView#findEnclosingBlockStart(FlowNode)}
     *
     * @return list of enclosing flow nodes starting with the passed flow nodes
     */
    @NonNull
    private Iterable<FlowNode> getAncestors(@NonNull final FlowNode flowNode) {
        List<FlowNode> ancestors = new ArrayList<>();
        FlowNode startNode;
        if (flowNode instanceof StepEndNode) {
            startNode = ((StepEndNode) flowNode).getStartNode();
        } else {
            startNode = flowNode;
        }
        ancestors.add(startNode);
        ancestors.addAll(startNode.getEnclosingBlocks());
        LOGGER.log(Level.FINEST, () -> "getAncestors(" + OtelUtils.toDebugString(flowNode) + "): " + ancestors.stream().map(OtelUtils.flowNodeToDebugString()).collect(Collectors.joining(", ")));
        return ancestors;
    }

    public void removePipelineStepSpan(@NonNull WorkflowRun run, @NonNull FlowNode flowNode, @NonNull Span span) {
        FlowNode startSpanNode;
        if (flowNode instanceof AtomNode) {
            startSpanNode = flowNode;
        } else if (flowNode instanceof StepEndNode) {
            StepEndNode stepEndNode = (StepEndNode) flowNode;
            startSpanNode = stepEndNode.getStartNode();
        } else if (flowNode instanceof StepStartNode && ((StepStartNode) flowNode).getDescriptor() instanceof ExecutorStep.DescriptorImpl) {
            // remove the "node.allocate" span, it's located on the parent node which is also a StepStartNode of a ExecutorStep.DescriptorImpl
            startSpanNode = flowNode.getParents().stream().findFirst().orElse(null);
            if (startSpanNode == null) {
                if (STRICT_MODE) {
                    throw new IllegalStateException("Parent node NOT found for " + OtelUtils.toDebugString(flowNode) + " on " + run);
                } else {
                    LOGGER.log(Level.WARNING, () -> "Parent node NOT found for " + OtelUtils.toDebugString(flowNode) + " on " + run);
                    return;
                }
            }
        } else {
            throw new VerifyException("Can't remove span from node of type" + flowNode.getClass() + " - " + flowNode);
        }

        ImmutableList.copyOf(startSpanNode.getActions(FlowNodeMonitoringAction.class))
            .reverse()
            .stream()
            .filter(flowNodeMonitoringAction -> Objects.equals(flowNodeMonitoringAction.getSpanId(), span.getSpanContext().getSpanId()))
            .findFirst()
            .ifPresentOrElse(
                FlowNodeMonitoringAction::purgeSpan,
                () -> {
                    String msg = "span not found to be purged: " + OtelUtils.toDebugString(span) +
                        " ending " + OtelUtils.toDebugString(startSpanNode) + " in " + run;
                    if (STRICT_MODE) {
                        throw new IllegalStateException(msg);
                    } else {
                        LOGGER.log(Level.WARNING, msg);
                    }
                });
    }


    public void removeJobPhaseSpan(@NonNull Run run, @NonNull Span span) {
    }

    public void removeBuildStepSpan(@NonNull AbstractBuild build, @NonNull BuildStep buildStep, @NonNull Span span) {
        ImmutableList.copyOf(build.getActions(BuildStepMonitoringAction.class))
            .reverse()
            .stream()
            .filter(buildStepMonitoringAction -> Objects.equals(buildStepMonitoringAction.getSpanId(), span.getSpanContext().getSpanId()))
            .findFirst().ifPresentOrElse(BuildStepMonitoringAction::purgeSpan, () -> {
                throw new IllegalStateException("span not found to be purged: " + span + " for " + buildStep);
            });
    }

    public void purgeRun(@NonNull Run run) {
        run.getActions(OtelMonitoringAction.class).forEach(OtelMonitoringAction::purgeSpan);
        // TODO verify we don't need this cleanup
        if (run instanceof WorkflowRun) {
            WorkflowRun workflowRun = (WorkflowRun) run;
            List<FlowNode> flowNodesHeads = Optional.ofNullable(workflowRun.getExecution()).map(FlowExecution::getCurrentHeads).orElse(Collections.emptyList());
            ForkScanner scanner = new ForkScanner();
            scanner.setup(flowNodesHeads);
            StreamSupport.stream(scanner.spliterator(), false).forEach(flowNode -> flowNode.getActions(OtelMonitoringAction.class).forEach(OtelMonitoringAction::purgeSpan));
        }
    }

    public void putSpan(@NonNull AbstractBuild build, @NonNull Span span) {
        build.addAction(new MonitoringAction(span));
        LOGGER.log(Level.FINEST, () -> "putSpan(" + build.getFullDisplayName() + "," + OtelUtils.toDebugString(span) + ")");
        writeToFile(build.getFullDisplayName() + "," + OtelUtils.toDebugString(span), "shouldnt_exist");
    }

    public void putSpan(AbstractBuild build, BuildStep buildStep, Span span) {
        build.addAction(new BuildStepMonitoringAction(span));
        LOGGER.log(Level.FINEST, () -> "putSpan(" + build.getFullDisplayName() + ", " + buildStep + "," + OtelUtils.toDebugString(span) + ")");
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("name", build.getFullDisplayName());
        node.put("parent", build.getParent().getFullName());
        node.put("type", "Build Phase Span");
        node.put("all-info", OtelUtils.toDebugString(span));
        node.put("build step", buildStep.toString());

        ReadableSpan readableSpan = (ReadableSpan) span;
        SpanData spanData = readableSpan.toSpanData();

        node.put("spanName", spanData.getName());
        node.put("spanId", spanData.getSpanId());
        node.put("parentSpanId", spanData.getParentSpanId());
        node.put("traceId", spanData.getTraceId());
        ObjectMapper mapper = new ObjectMapper();
        JsonNode map = mapper.valueToTree(spanData.getAttributes().asMap());
        node.put("attributesMap", map);
        try {
            node.put("parameterMap", mapper.readTree(spanData.getAttributes().asMap().get(AttributeKey.stringKey("harness-attribute")).toString()));
        } catch (Exception ignored) {
        }
        writeToFile(node.toPrettyString(), spanData.getTraceId() + "-" + spanData.getSpanId());
    }

    public void putSpan(@NonNull Run run, @NonNull Span span) {
        run.addAction(new MonitoringAction(span));
        LOGGER.log(Level.FINEST, () -> "putSpan(" + run.getFullDisplayName() + "," + OtelUtils.toDebugString(span) + ")");
        ReadableSpan readableSpan = (ReadableSpan) span;
        SpanData spanData = readableSpan.toSpanData();
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("name", run.getFullDisplayName());
        node.put("parentSpanId", spanData.getParentSpanId());
        node.put("traceId", spanData.getTraceId());
        node.put("spanName", spanData.getName());
        node.put("spanId", spanData.getSpanId());
        writeToFile(node.toPrettyString(), spanData.getTraceId()+ "-" + spanData.getSpanId());
    }

    public void putRunPhaseSpan(@NonNull Run run, @NonNull Span span) {
        run.addAction(new RunPhaseMonitoringAction(span));
        LOGGER.log(Level.FINEST, () -> "putRunPhaseSpan(" + run.getFullDisplayName() + "," + OtelUtils.toDebugString(span) + ")");
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("name", run.getFullDisplayName());
        node.put("parent", run.getParent().getFullName());
        node.put("type", "Run Phase Span");
        node.put("all-info", OtelUtils.toDebugString(span));
        ReadableSpan readableSpan = (ReadableSpan) span;
        SpanData spanData = readableSpan.toSpanData();
        node.put("spanName", spanData.getName());
        node.put("spanId", spanData.getSpanId());
        node.put("parentSpanId", spanData.getParentSpanId());
        node.put("traceId", spanData.getTraceId());
        ObjectMapper mapper = new ObjectMapper();
        JsonNode map = mapper.valueToTree(spanData.getAttributes().asMap());
        node.put("attributesMap", map);
        try {
            node.put("parameterMap", mapper.readTree(spanData.getAttributes().asMap().get(AttributeKey.stringKey("harness-attribute")).toString()));
        } catch (Exception ignored) {
        }
        writeToFile(node.toPrettyString(), spanData.getTraceId()+ "-" + spanData.getSpanId());
    }

    public void putSpan(@NonNull Run run, @NonNull Span span, @NonNull FlowNode flowNode) {
        // FYI for agent allocation, we have 2 FlowNodeMonitoringAction to track the agent allocation duration
        flowNode.addAction(new FlowNodeMonitoringAction(span));

        LOGGER.log(Level.FINE, () -> "putSpan(" + run.getFullDisplayName() + ", " +
            OtelUtils.toDebugString(flowNode) + ", " + OtelUtils.toDebugString(span) + ")");

        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("name", run.getFullDisplayName());
        node.put("parent", run.getParent().getFullName());
        node.put("type", "Run Phase Span");
        ReadableSpan readableSpan = (ReadableSpan) span;
        SpanData spanData = readableSpan.toSpanData();
        node.put("spanName", spanData.getName());
        node.put("spanId", spanData.getSpanId());
        node.put("parentSpanId", spanData.getParentSpanId());
        node.put("traceId", spanData.getTraceId());
        ObjectMapper mapper = new ObjectMapper();
        JsonNode map = mapper.valueToTree(spanData.getAttributes().asMap());
        node.put("attributesMap", map);
        try {
            node.put("parameterMap", mapper.readTree(spanData.getAttributes().asMap().get(AttributeKey.stringKey("harness-attribute")).toString()));
        } catch (Exception ignored) {
            
        }
        node.put("all-info", OtelUtils.toDebugString(span));
        writeToFile(node.toPrettyString(), spanData.getTraceId()+ "-" + spanData.getSpanId());
    }

    static public OtelTraceService get() {
        return ExtensionList.lookupSingleton(OtelTraceService.class);
    }

    private void writeToFile(String content, String fileName) {
        fileName = fileName.replaceAll("[^a-zA-Z0-9.-]", "_");
        try {
            File myObj = new File("/Users/jamie/opentelemetry-plugin/result/trace_" + fileName);
            if (myObj.createNewFile()) {
                FileWriter myWriter = new FileWriter(myObj);
                myWriter.write(content);
                myWriter.close();
            } else {
                System.out.println("File already exists.");
            }
        } catch (IOException ignore) {}
    }
}
