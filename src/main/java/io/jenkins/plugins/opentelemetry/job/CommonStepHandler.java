/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import com.google.common.annotations.VisibleForTesting;
import hudson.Extension;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import jenkins.YesNoMaybe;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import javax.annotation.Nonnull;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Customization of spans for dynamically add attributes to the steps.
 */
@Extension(optional = true, dynamicLoadable = YesNoMaybe.YES)
public class CommonStepHandler implements StepHandler {
    private final static Logger LOGGER = Logger.getLogger(CommonStepHandler.class.getName());

    @Override
    public boolean canCreateSpanBuilder(@Nonnull FlowNode flowNode, @Nonnull WorkflowRun run) {
        return flowNode instanceof StepAtomNode;
    }

    @Nonnull
    @Override
    public SpanBuilder createSpanBuilder(@Nonnull FlowNode node, @Nonnull  WorkflowRun run, @Nonnull Tracer tracer) throws Exception {
        final Map<String, Object> arguments = ArgumentsAction.getFilteredArguments(node);
        final String displayFunctionName = node.getDisplayFunctionName();
        return this.createSpanBuilderFromApmDetails(node, tracer.spanBuilder(displayFunctionName));
    }

    /**
     * Visible for testing.
     */
    @VisibleForTesting
    protected SpanBuilder createSpanBuilderFromApmDetails(@Nonnull FlowNode node, @Nonnull SpanBuilder spanBuilder) throws URISyntaxException {
        final Map<String, Object> arguments = ArgumentsAction.getFilteredArguments(node);
        final String apm = Objects.toString(arguments.get("apm"), null);
        Map<String, String> apmAttributes = (Map<String, String>) arguments.get("apm");
        if(apmAttributes != null && apmAttributes.size() > 0) {
            LOGGER.log(Level.INFO, () -> "Create apm " + apmAttributes.toString());
            // TODO: validate the attributes and only support the ones that the OTEL provides.
            apmAttributes.forEach(spanBuilder::setAttribute);
        }
        return spanBuilder;
    }
}
