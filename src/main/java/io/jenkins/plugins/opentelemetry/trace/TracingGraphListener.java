package io.jenkins.plugins.opentelemetry.trace;

import hudson.Extension;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.GraphListener;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.scm.SCMStep;

import javax.annotation.Nonnull;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class TracingGraphListener extends OtelContextAwarePipelineStepListener implements GraphListener.Synchronous {
    private final static Logger LOGGER = Logger.getLogger(TracingGraphListener.class.getName());

    @Override
    protected void onAtomicStep(StepAtomNode node, @Nonnull WorkflowRun run) {
        StepDescriptor stepAtomNodeDescriptor = node.getDescriptor();
        if (stepAtomNodeDescriptor instanceof SCMStep.SCMStepDescriptor) {
            SCMStep.SCMStepDescriptor scmStepDescriptor = (SCMStep.SCMStepDescriptor) stepAtomNodeDescriptor;
            LOGGER.log(Level.INFO, "{2} - SCM Step - name:{1}, displayFunctionName:{2} - {0}", new Object[]{node, node.getDisplayName(), node.getDisplayFunctionName(), scmStepDescriptor});
        } else {
            LOGGER.log(Level.INFO, "{2} - onNewHead - name:{1}, displayFunctionName:{2} - {0}", new Object[]{node, node.getDisplayName(), node.getDisplayFunctionName()});
        }
    }

    @Override
    protected void onStartPreStageStep(@Nonnull StepStartNode stepStartNode, @Nonnull String stageName, @Nonnull WorkflowRun run) {
        Span stageSpan = getTracer().spanBuilder(stageName)
                .setParent(Context.current())
                .setAttribute("jenkins.pipeline.step.type", "stage")
                .startSpan();
        getTracerService().putSpan(run, stageSpan);
    }

    @Override
    protected void onStartStageStep(@Nonnull StepStartNode stepStartNode, @Nonnull String stageName, @Nonnull WorkflowRun run) {
    }

    @Override
    protected void onEndStageStep(@Nonnull StepEndNode stepEndNode, @Nonnull WorkflowRun run) {
    }

    @Override
    protected void onEndPreStageStep(@Nonnull StepEndNode stepEndNode, @Nonnull WorkflowRun run) {
        Span stageSpan = getTracerService().getSpan(run);
        stageSpan.end();
        getTracerService().removeSpan(run, stageSpan);
    }



}
