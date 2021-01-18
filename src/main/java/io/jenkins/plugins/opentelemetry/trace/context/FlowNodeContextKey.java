package io.jenkins.plugins.opentelemetry.trace.context;

import hudson.model.Run;
import io.opentelemetry.context.ContextKey;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.concurrent.Immutable;

/**
 * See {@code io.opentelemetry.api.trace.SpanContextKey}
 */
@Immutable
public final class FlowNodeContextKey {
    public static final ContextKey<FlowNode> KEY = ContextKey.named(FlowNodeContextKey.class.getName());

    private FlowNodeContextKey(){}
}
